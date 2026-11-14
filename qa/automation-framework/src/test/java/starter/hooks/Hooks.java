package starter.hooks;

import io.cucumber.java.BeforeAll;
import starter.utils.EnvConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Pre-flight check: make sure the Ella API is up before any scenario runs.
 * Without it, a stopped API shows up as dozens of "Connection refused" step failures
 * followed by misleading "No product id saved" errors.
 */
public class Hooks {

    private static final int ATTEMPTS = 5;
    private static final Duration WAIT_BETWEEN_ATTEMPTS = Duration.ofSeconds(2);

    @BeforeAll
    public static void apiMustBeReachable() throws InterruptedException {
        String baseUrl = EnvConfig.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("No base URL for environment '" + EnvConfig.getEnvironmentName()
                    + "'. Add environments." + EnvConfig.getEnvironmentName() + ".base.url to serenity.conf.");
        }
        String healthUrl = baseUrl.replaceAll("/+$", "") + "/health";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofSeconds(5)).GET().build();

        String lastProblem = "";
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    System.out.println("[pre-flight] Ella API is up: " + healthUrl);
                    return;
                }
                lastProblem = "HTTP " + response.statusCode() + " " + response.body();
            } catch (java.io.IOException e) {
                lastProblem = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            }
            if (attempt < ATTEMPTS) Thread.sleep(WAIT_BETWEEN_ATTEMPTS.toMillis());
        }

        throw new IllegalStateException(String.join(System.lineSeparator(),
                "",
                "============================================================",
                " Ella API is not reachable at " + healthUrl,
                " Last result: " + lastProblem,
                "",
                " Start the API first (from the repo root), then rerun:",
                "   docker compose up -d --build --wait",
                "   or without Docker:  cd qa/performance && npm install && npm run api:start",
                " Another environment: mvn clean install -Dserenity.environment=<env>",
                "============================================================"));
    }
}
