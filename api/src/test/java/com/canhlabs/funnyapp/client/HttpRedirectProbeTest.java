package com.canhlabs.funnyapp.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRedirectProbeTest {

    private HttpServer server;
    private String base;
    private final AtomicReference<String> method = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/abs", ex -> {
            method.set(ex.getRequestMethod());
            ex.getResponseHeaders().add("Location", "https://www.facebook.com/reel/1987308575243562/?rdid=x");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/rel", ex -> {
            ex.getResponseHeaders().add("Location", "/reel/123/");
            ex.sendResponseHeaders(301, -1);
            ex.close();
        });
        server.createContext("/badloc", ex -> {
            ex.getResponseHeaders().add("Location", "https://bad host/x");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/noloc", ex -> {
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void returnsTheLocationOfARedirect_usingHead() {
        assertThat(new HttpRedirectProbe().location(base + "/abs"))
                .contains("https://www.facebook.com/reel/1987308575243562/?rdid=x");
        assertThat(method.get()).isEqualTo("HEAD");
    }

    @Test
    void resolvesARelativeLocationAgainstTheProbedUrl() {
        assertThat(new HttpRedirectProbe().location(base + "/rel")).contains(base + "/reel/123/");
    }

    @Test
    void emptyWhenNotARedirect() {
        HttpRedirectProbe probe = new HttpRedirectProbe(HttpClient.newHttpClient(), new Semaphore(1));

        assertThat(probe.location(base + "/ok")).isEmpty();
        assertThat(probe.location(base + "/noloc")).isEmpty();
        assertThat(probe.location(base + "/badloc")).isEmpty();
    }

    @Test
    void emptyForAnUnparseableUrl() {
        assertThat(new HttpRedirectProbe().location("not a url")).isEmpty();
    }

    @Test
    void connectionError_isUnavailable() {
        server.stop(0);

        assertThatThrownBy(() -> new HttpRedirectProbe().location(base + "/abs"))
                .isInstanceOf(RedirectProbe.Unavailable.class)
                .hasMessage("io");
    }

    @Test
    void noFreePermit_isUnavailable_withoutCallingTheServer() {
        HttpRedirectProbe probe = new HttpRedirectProbe(HttpClient.newHttpClient(), new Semaphore(0));

        assertThatThrownBy(() -> probe.location(base + "/abs"))
                .isInstanceOf(RedirectProbe.Unavailable.class)
                .hasMessage("busy");
        assertThat(method.get()).isNull();
    }

    @Test
    void interrupted_isUnavailable_andKeepsTheInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> new HttpRedirectProbe().location(base + "/abs"))
                    .isInstanceOf(RedirectProbe.Unavailable.class);
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }

    @Test
    void permitIsReleasedAfterEachProbe() {
        Semaphore permits = new Semaphore(1);
        HttpRedirectProbe probe = new HttpRedirectProbe(HttpClient.newHttpClient(), permits);

        probe.location(base + "/abs");
        probe.location(base + "/ok");

        assertThat(permits.availablePermits()).isEqualTo(1);
    }
}
