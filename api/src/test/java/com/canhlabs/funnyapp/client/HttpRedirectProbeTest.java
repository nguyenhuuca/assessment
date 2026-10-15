package com.canhlabs.funnyapp.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

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
        HttpRedirectProbe probe = new HttpRedirectProbe(HttpClient.newHttpClient());

        assertThat(probe.location(base + "/ok")).isEmpty();
        assertThat(probe.location(base + "/noloc")).isEmpty();
    }

    @Test
    void emptyOnConnectionErrorOrBadUrl() {
        server.stop(0);

        assertThat(new HttpRedirectProbe().location(base + "/abs")).isEmpty();
        assertThat(new HttpRedirectProbe().location("not a url")).isEmpty();
    }

    @Test
    void emptyWhenInterrupted() {
        Thread.currentThread().interrupt();
        try {
            assertThat(new HttpRedirectProbe().location(base + "/abs")).isEmpty();
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
