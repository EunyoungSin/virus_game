package com.checkpoint.ai.gemini;

import com.checkpoint.ai.AiDialogueClient;
import com.checkpoint.ai.ChatTurn;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Service
public class GeminiDialogueClient implements AiDialogueClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String primaryModel;
    private final String fallbackModel;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final int maxOutputTokens;

    public GeminiDialogueClient(
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model}") String primaryModel,
            @Value("${gemini.fallback-model:}") String fallbackModel,
            @Value("${gemini.max-retries:3}") int maxRetries,
            @Value("${gemini.initial-backoff-ms:500}") long initialBackoffMs,
            @Value("${gemini.max-output-tokens:1024}") int maxOutputTokens) {
        this.restClient = RestClient.create(baseUrl);
        this.apiKey = apiKey;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.maxOutputTokens = maxOutputTokens;
    }

    @Override
    public String generateResponse(String systemPrompt, List<ChatTurn> history, String userQuestion) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key is not configured");
        }
        GeminiRequest request = buildRequest(systemPrompt, history, userQuestion);
        try {
            return callModel(primaryModel, request);
        } catch (RuntimeException primaryFailure) {
            if (fallbackModel.isBlank()) {
                throw primaryFailure;
            }
            return callModel(fallbackModel, request);
        }
    }

    private String callModel(String model, GeminiRequest request) {
        long backoff = initialBackoffMs;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                GeminiResponse response =
                        restClient
                                .post()
                                .uri("/{model}:generateContent?key={key}", model, apiKey)
                                .body(request)
                                .retrieve()
                                .body(GeminiResponse.class);
                return extractText(response);
            } catch (HttpClientErrorException.TooManyRequests rateLimited) {
                if (attempt == maxRetries) {
                    throw rateLimited;
                }
                sleep(backoff);
                backoff *= 2;
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private GeminiRequest buildRequest(String systemPrompt, List<ChatTurn> history, String userQuestion) {
        List<Content> contents = new ArrayList<>();
        for (ChatTurn turn : history) {
            contents.add(new Content(turn.role(), List.of(new Part(turn.text()))));
        }
        contents.add(new Content("user", List.of(new Part(userQuestion))));
        return new GeminiRequest(
                new SystemInstruction(List.of(new Part(systemPrompt))),
                contents,
                new GenerationConfig(0.8, maxOutputTokens));
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new IllegalStateException("gemini returned no candidates");
        }
        Content content = response.candidates().get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            throw new IllegalStateException("gemini returned empty content");
        }
        return content.parts().get(0).text();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while backing off", e);
        }
    }
}
