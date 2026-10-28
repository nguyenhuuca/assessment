package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.config.JsonMappers;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.canhlabs.funnyapp.client.GitHubIssueClient.GitHubException;
import com.canhlabs.funnyapp.client.GitHubIssueClient.IssueState;
import com.canhlabs.funnyapp.config.AppProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdkGitHubIssueClientTest {

    private static final String TOKEN = "ghp_FAKE_TOKEN_123";
    private static final String SECRET_BODY = "secret-response-body";

    private final JsonMapper mapper = JsonMappers.create();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> accept = new AtomicReference<>();
    private final AtomicReference<String> version = new AtomicReference<>();
    private final AtomicReference<String> reqBody = new AtomicReference<>();
    private final AtomicInteger createStatus = new AtomicInteger(201);
    private HttpServer server;
    private URI base;
    private AppProperties props;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void start() throws IOException {
        props = new AppProperties();
        props.getGithub().setToken(TOKEN);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/nguyenhuuca/assessment/issues", ex -> {
            requests.incrementAndGet();
            method.set(ex.getRequestMethod());
            path.set(ex.getRequestURI().getPath());
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            accept.set(ex.getRequestHeaders().getFirst("Accept"));
            version.set(ex.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
            reqBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String p = ex.getRequestURI().getPath();
            int status;
            String body;
            if (p.endsWith("/issues")) {
                status = createStatus.get();
                if (status == 999) {
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    status = 201;
                }
                body = status == 201
                        ? "{\"number\":42,\"html_url\":\"https://github.com/nguyenhuuca/assessment/issues/42\"}"
                        : "{\"message\":\"" + SECRET_BODY + "\"}";
            } else if (p.endsWith("/1")) {
                status = 200;
                body = "{\"state\":\"open\"}";
            } else if (p.endsWith("/2")) {
                status = 200;
                body = "{\"state\":\"closed\"}";
            } else if (p.endsWith("/404")) {
                status = 404;
                body = "{}";
            } else if (p.endsWith("/410")) {
                status = 410;
                body = "{}";
            } else if (p.endsWith("/3")) {
                ex.getResponseHeaders().add("Location", "/elsewhere");
                status = 301;
                body = "";
            } else {
                status = 200;
                body = "not json";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());

        logger = (Logger) LoggerFactory.getLogger(JdkGitHubIssueClient.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void stop() {
        logger.detachAppender(logs);
        server.stop(0);
    }

    private JdkGitHubIssueClient client() {
        return client(Duration.ofSeconds(5));
    }

    private JdkGitHubIssueClient client(Duration timeout) {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new JdkGitHubIssueClient(http, base, timeout, props, mapper);
    }

    private void assertNoTokenInLogs() {
        assertThat(logs.list).noneMatch(e -> e.getFormattedMessage().contains(TOKEN)
                || e.getFormattedMessage().contains(SECRET_BODY));
    }

    @Test
    void create_sendsExpectedRequestAndParsesResponse() throws Exception {
        var issue = client().create("T", "B", List.of("report", "bug"));

        assertThat(issue.number()).isEqualTo(42);
        assertThat(issue.htmlUrl()).isEqualTo("https://github.com/nguyenhuuca/assessment/issues/42");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/repos/nguyenhuuca/assessment/issues");
        assertThat(auth.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(accept.get()).isEqualTo("application/vnd.github+json");
        assertThat(version.get()).isEqualTo("2022-11-28");
        JsonNode sent = mapper.readTree(reqBody.get());
        assertThat(sent.get("title").asText()).isEqualTo("T");
        assertThat(sent.get("body").asText()).isEqualTo("B");
        assertThat(sent.get("labels")).hasSize(2);
        assertThat(sent.get("labels").get(0).asText()).isEqualTo("report");
    }

    @Test
    void create_notConfigured_throwsWithoutHttpCall() {
        props.getGithub().setToken(" ");

        assertThatThrownBy(() -> client().create("T", "B", List.of()))
                .isInstanceOfSatisfying(GitHubException.class,
                        e -> assertThat(e.getCode()).isEqualTo("github.notConfigured"));
        assertThat(requests.get()).isZero();
    }

    @Test
    void create_unprocessable_mapsToHttpCodeWithoutBodyOrToken() {
        createStatus.set(422);

        assertThatThrownBy(() -> client().create("T", "B", List.of()))
                .isInstanceOfSatisfying(GitHubException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("github.http.422");
                    assertThat(e.getMessage()).doesNotContain(TOKEN).doesNotContain(SECRET_BODY);
                });
        assertNoTokenInLogs();
    }

    @Test
    void create_serverError_mapsToHttp500() {
        createStatus.set(500);

        assertThatThrownBy(() -> client().create("T", "B", List.of()))
                .isInstanceOfSatisfying(GitHubException.class,
                        e -> assertThat(e.getCode()).isEqualTo("github.http.500"));
    }

    @Test
    void create_slowServer_mapsToTimeout() {
        createStatus.set(999);

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).create("T", "B", List.of()))
                .isInstanceOfSatisfying(GitHubException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("github.timeout");
                    assertThat(e.getMessage()).doesNotContain(TOKEN);
                });
        assertNoTokenInLogs();
    }

    @Test
    void create_connectionRefused_mapsToIo() {
        server.stop(0);

        assertThatThrownBy(() -> client().create("T", "B", List.of()))
                .isInstanceOfSatisfying(GitHubException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("github.io");
                    assertThat(e.getMessage()).doesNotContain(TOKEN);
                });
        assertNoTokenInLogs();
    }

    @Test
    void state_mapsOpenAndClosed() {
        assertThat(client().state(1)).isEqualTo(IssueState.OPEN);
        assertThat(method.get()).isEqualTo("GET");
        assertThat(auth.get()).isEqualTo("Bearer " + TOKEN);
        assertThat(client().state(2)).isEqualTo(IssueState.CLOSED);
    }

    @Test
    void state_notFoundGoneRedirectAndGarbage_areUnknown() {
        assertThat(client().state(404)).isEqualTo(IssueState.UNKNOWN);
        assertThat(client().state(410)).isEqualTo(IssueState.UNKNOWN);
        assertThat(client().state(3)).isEqualTo(IssueState.UNKNOWN);
        assertThat(client().state(7)).isEqualTo(IssueState.UNKNOWN);
    }

    @Test
    void state_exception_isUnknownAndLogsNoToken() {
        server.stop(0);

        assertThat(client().state(1)).isEqualTo(IssueState.UNKNOWN);
        assertNoTokenInLogs();
    }

    @Test
    void state_notConfigured_isUnknownWithoutHttpCall() {
        props.getGithub().setToken("");

        assertThat(client().state(1)).isEqualTo(IssueState.UNKNOWN);
        assertThat(requests.get()).isZero();
    }
}
