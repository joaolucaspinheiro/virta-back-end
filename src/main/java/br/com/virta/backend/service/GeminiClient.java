package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/** Low-level client for the Gemini API. The API key never leaves the back-end. */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient http = RestClient.create("https://generativelanguage.googleapis.com/v1beta");
    private final String apiKey;
    private final String model;

    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-3.6-flash}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Sends the conversation and the available tools; returns the parts of the
     * model's answer (text and/or function calls), exactly as Gemini sent them.
     */
    public List<Map<String, Object>> generate(String instructions,
                                              List<Map<String, Object>> contents,
                                              List<Map<String, Object>> functionDeclarations) {
        if (apiKey.isBlank()) {
            throw new BusinessException("The assistant is not configured.");
        }
        Map<String, Object> body = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instructions))),
                "contents", contents,
                "tools", List.of(Map.of("functionDeclarations", functionDeclarations)),
                "generationConfig", Map.of("temperature", 0.2));

        GeminiResponse response;
        try {
            response = http.post()
                    .uri("/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);
        } catch (RestClientException e) {
            log.error("Gemini call failed", e);
            throw new BusinessException("The assistant is unavailable right now. Try again in a moment.");
        }

        if (response == null || response.candidates() == null || response.candidates().isEmpty()
                || response.candidates().get(0).content() == null) {
            throw new BusinessException("The assistant returned no answer.");
        }
        return response.candidates().get(0).content().parts();
    }

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(String role, List<Map<String, Object>> parts) {}
}