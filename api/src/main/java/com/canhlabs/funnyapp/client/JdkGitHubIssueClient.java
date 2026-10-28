package com.canhlabs.funnyapp.client;

import com.canhlabs.funnyapp.config.AppProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** GitHub REST client on the JDK HttpClient; redirects are never followed. */
@Slf4j
@Component
public class JdkGitHubIssueClient implements GitHubIssueClient {

    static final URI API_BASE = URI.create("https://api.github.com");
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;
    private final URI base;
    private final Duration requestTimeout;
    private final AppProperties props;
    private final JsonMapper mapper;

    @Autowired
    public JdkGitHubIssueClient(AppProperties props, JsonMapper mapper) {
        this(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build(), API_BASE, REQUEST_TIMEOUT, props, mapper);
    }

    JdkGitHubIssueClient(HttpClient client, URI base, Duration requestTimeout,
                         AppProperties props, JsonMapper mapper) {
        this.client = client;
        this.base = base;
        this.requestTimeout = requestTimeout;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public CreatedIssue create(String title, String body, List<String> labels) {
        AppProperties.Github gh = props.getGithub();
        if (gh == null || !gh.isConfigured()) {
            throw new GitHubException("github.notConfigured");
        }
        try {
            String json = mapper.writeValueAsString(Map.of("title", title, "body", body, "labels", labels));
            HttpRequest request = builder(gh, "/issues")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                log.warn("GitHub create issue failed: HTTP {}", response.statusCode());
                throw new GitHubException("github.http." + response.statusCode());
            }
            JsonNode node = mapper.readTree(response.body());
            if (!node.hasNonNull("number") || !node.hasNonNull("html_url")) {
                throw new GitHubException("github.io");
            }
            return new CreatedIssue(node.get("number").asInt(), node.get("html_url").asText());
        } catch (GitHubException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            log.warn("GitHub create issue timed out");
            throw new GitHubException("github.timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubException("github.io");
        } catch (IOException | RuntimeException e) {
            log.warn("GitHub create issue failed: {}", e.getClass().getSimpleName());
            throw new GitHubException("github.io");
        }
    }

    @Override
    public IssueState state(int number) {
        AppProperties.Github gh = props.getGithub();
        if (gh == null || !gh.isConfigured()) {
            return IssueState.UNKNOWN;
        }
        try {
            HttpRequest request = builder(gh, "/issues/" + number).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return IssueState.UNKNOWN;
            }
            String state = mapper.readTree(response.body()).path("state").asText("");
            return switch (state) {
                case "open" -> IssueState.OPEN;
                case "closed" -> IssueState.CLOSED;
                default -> IssueState.UNKNOWN;
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IssueState.UNKNOWN;
        } catch (IOException | RuntimeException e) {
            log.info("GitHub issue state lookup failed: {}", e.getClass().getSimpleName());
            return IssueState.UNKNOWN;
        }
    }

    private HttpRequest.Builder builder(AppProperties.Github gh, String suffix) {
        URI uri = URI.create(base.toString().replaceAll("/+$", "")
                + "/repos/" + gh.getOwner() + "/" + gh.getRepo() + suffix);
        return HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Authorization", "Bearer " + gh.getToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }
}
