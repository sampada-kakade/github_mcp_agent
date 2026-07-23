package com.example.githubmcpagent.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class DockerService {

    public String runMcpDocker(String githubToken, String openAiKey, String query) {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-i");
        cmd.add("--rm");
        cmd.add("-e");
        cmd.add("GITHUB_PERSONAL_ACCESS_TOKEN");
        cmd.add("-e");
        cmd.add("OPENAI_API_KEY");
        cmd.add("-e");
        cmd.add("GITHUB_TOOLSETS");
        cmd.add("ghcr.io/github/github-mcp-server");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        env.putAll(System.getenv());
        if (githubToken != null)
            env.put("GITHUB_PERSONAL_ACCESS_TOKEN", githubToken);
        if (openAiKey != null)
            env.put("OPENAI_API_KEY", openAiKey);
        env.put("GITHUB_TOOLSETS", "repos,issues,pull_requests");
        if (query != null && !query.isBlank()) {
            env.put("MCP_QUERY", query);
        }

        try {
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                long start = System.currentTimeMillis();
                while ((line = reader.readLine()) != null) {
                    out.append(line).append("\n");
                    // simple safety to stop if long-running; also check timeout
                    if (System.currentTimeMillis() - start > Duration.ofSeconds(120).toMillis()) {
                        p.destroyForcibly();
                        return "Error: Request timed out after 120 seconds";
                    }
                }
            }

            try (BufferedReader err = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = err.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }

            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return "Error: Request timed out after 120 seconds";
            }

            int code = p.exitValue();
            out.append("\n-- Process exit code: ").append(code);
            return out.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
