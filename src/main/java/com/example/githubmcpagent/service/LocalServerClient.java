package com.example.githubmcpagent.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class LocalServerClient {

    private static final String TARGET_URL = "http://localhost:8080/run";
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_BACKOFF_MS = 1000;
    private static final String SUMMARY_LOG_FILE = "client.log";

    private final int maxRetries;
    private final int backoffMs;

    public LocalServerClient(int maxRetries, int backoffMs) {
        this.maxRetries = maxRetries;
        this.backoffMs = backoffMs;
    }

    public void sendQuery(String query, String repo) {
        String formData;
        try {
            formData = "query=" + URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                    + "&repo=" + URLEncoder.encode(repo, StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            logError("Failed to encode query parameters", e);
            return;
        }

        logInfo("Prepared request body:");
        logIndented(formData);

        int attempt = 1;
        while (attempt <= maxRetries) {
            logInfo(String.format("Attempt %d of %d: POST %s", attempt, maxRetries, TARGET_URL));
            HttpURLConnection connection = null;
            long startNanos = System.nanoTime();
            int responseCode = -1;
            String responseBody = "";
            try {
                URL url = new URL(TARGET_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setRequestProperty("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

                byte[] postDataBytes = formData.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", String.valueOf(postDataBytes.length));

                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(postDataBytes);
                    outputStream.flush();
                }

                responseCode = connection.getResponseCode();
                responseBody = readResponseBody(connection, responseCode);
                long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();

                logInfo("Received response code: " + responseCode);
                logInfo("Elapsed time: " + elapsedMs + " ms");
                logInfo("Response body:");
                logIndented(responseBody);

                saveResponse(responseBody);
                appendSummary(responseCode, query);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    logInfo("Request succeeded.");
                    return;
                }

                logWarning(String.format("Server returned non-200 status code (%d). Retrying after %d ms.",
                        responseCode, backoffMs));
            } catch (IOException e) {
                long elapsedMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
                logError("Error sending query to local server", e);
                logInfo("Elapsed time: " + elapsedMs + " ms");
                appendSummary(responseCode, query);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

            attempt++;
            if (attempt <= maxRetries) {
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logError("Retry delay interrupted", ie);
                    return;
                }
            }
        }

        logWarning("Exceeded maximum retry attempts without receiving a 200 OK response.");
    }

    private String readResponseBody(HttpURLConnection connection, int responseCode) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseCode >= HttpURLConnection.HTTP_BAD_REQUEST
                        ? connection.getErrorStream()
                        : connection.getInputStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line).append(System.lineSeparator());
            }
            return responseBuilder.toString().trim();
        }
    }

    private void saveResponse(String responseBody) {
        String timestamp = ZonedDateTime.now(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS"));
        String fileName = "response_" + timestamp + ".json";
        Path filePath = Path.of(fileName);
        try {
            Files.writeString(filePath, responseBody, StandardCharsets.UTF_8);
            logInfo("Saved response to " + fileName);
        } catch (IOException e) {
            logError("Failed to save response file", e);
        }
    }

    private void appendSummary(int responseCode, String query) {
        String logLine = String.format("%s | status=%d | query=%s%s", timestamp(), responseCode, escapeQuery(query),
                System.lineSeparator());
        Path logPath = Path.of(SUMMARY_LOG_FILE);
        try {
            Files.writeString(logPath, logLine, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logError("Failed to append summary to " + SUMMARY_LOG_FILE, e);
        }
    }

    private String escapeQuery(String query) {
        return query.replace("\n", "\\n").replace("\r", "\\r").replace("|", "\\|");
    }

    private void logInfo(String message) {
        System.out.println(String.format("[%s] INFO: %s", timestamp(), message));
    }

    private void logWarning(String message) {
        System.out.println(String.format("[%s] WARN: %s", timestamp(), message));
    }

    private void logError(String message, Exception e) {
        System.err.println(String.format("[%s] ERROR: %s - %s", timestamp(), message, e.getMessage()));
        e.printStackTrace(System.err);
    }

    private void logIndented(String text) {
        for (String line : text.split("\r?\n")) {
            System.out.println("    " + line);
        }
    }

    private String timestamp() {
        return ZonedDateTime.now(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static void main(String[] args) {
        String query = null;
        String repo = "Shubhamsaboo/awesome-llm-apps";
        int maxRetries = DEFAULT_MAX_RETRIES;
        int backoffMs = DEFAULT_BACKOFF_MS;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--query":
                    if (i + 1 < args.length) {
                        query = args[++i];
                    }
                    break;
                case "--repo":
                    if (i + 1 < args.length) {
                        repo = args[++i];
                    }
                    break;
                case "--retries":
                    if (i + 1 < args.length) {
                        maxRetries = Integer.parseInt(args[++i]);
                    }
                    break;
                case "--backoff-ms":
                    if (i + 1 < args.length) {
                        backoffMs = Integer.parseInt(args[++i]);
                    }
                    break;
                default:
                    if (query == null) {
                        query = args[i];
                    }
                    break;
            }
        }

        if (query == null || query.isBlank()) {
            query = "Show me recent merged PRs";
        }

        LocalServerClient client = new LocalServerClient(maxRetries, backoffMs);
        client.sendQuery(query, repo);
    }
}
