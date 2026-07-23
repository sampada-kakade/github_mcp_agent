package com.example.githubmcpagent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service
public class OpenAIService {
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public String generateText(String prompt, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return "[OpenAI API key not set]";
        }

        try {
            ObjectNodeBuilder builder = new ObjectNodeBuilder();
            String bodyJson = builder.buildChatRequest(prompt);

            RequestBody body = RequestBody.create(bodyJson, MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(OPENAI_CHAT_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "[OpenAI request failed: " + response.code() + "]";
                }
                String resp = response.body().string();
                JsonNode root = mapper.readTree(resp);
                JsonNode choices = root.path("choices");
                if (choices.isArray() && choices.size() > 0) {
                    JsonNode first = choices.get(0);
                    JsonNode message = first.path("message");
                    String content = message.path("content").asText();
                    if (content == null || content.isEmpty()) {
                        // some models use 'text' instead
                        content = first.path("text").asText();
                    }
                    return content;
                }
                return "";
            }
        } catch (IOException e) {
            return "[OpenAI request error: " + e.getMessage() + "]";
        }
    }

    // tiny builder to avoid depending on heavy Jackson models publicly
    private static class ObjectNodeBuilder {
        private final ObjectMapper mapper = new ObjectMapper();

        public String buildChatRequest(String prompt) throws IOException {
            // Use gpt-3.5-turbo compatible shape
            JsonNode root = mapper.createObjectNode();
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("model", "gpt-3.5-turbo");
            com.fasterxml.jackson.databind.node.ArrayNode messages = mapper.createArrayNode();
            com.fasterxml.jackson.databind.node.ObjectNode userMsg = mapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).set("messages", messages);
            ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("max_tokens", 300);
            return mapper.writeValueAsString(root);
        }
    }
}
