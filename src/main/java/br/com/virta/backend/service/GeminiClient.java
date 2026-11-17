package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Low-level client for the Gemini API. The API key never leaves the back-end.
 *
 * <p>Resilience: each call has a timeout; when Gemini is overloaded (429/5xx) the
 * same model is retried with a growing wait (backoff), and if it still fails the
 * optional fallback model is tried before giving up.
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    static final int MAX_ATTEMPTS_PER_MODEL = 3;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient http;
    private final String apiKey;
    private final List<String> models;
    private final long backoffMillis;

    @Autowired
    public GeminiClient(@Value("${gemini.api-key:}") String apiKey,
                        @Value("${gemini.model:gemini-3.6-flash}") String model,
                        @Value("${gemini.fallback-model:}") String fallbackModel) {
        this(RestClient.builder().baseUrl(BASE_URL).requestFactory(timeoutFactory()),
                apiKey, model, fallbackModel, 1000);
    }

    /** Used by the tests, which plug in a fake server and a zero backoff. */
    GeminiClient(RestClient.Builder builder, String apiKey, String model,
                 String fallbackModel, long backoffMillis) {
        this.http = builder.build();
        this.apiKey = apiKey;
        this.models = new ArrayList<>(List.of(model.trim()));
        if (fallbackModel != null && !fallbackModel.isBlank() && !fallbackModel.trim().equals(model.trim())) {
            this.models.add(fallbackModel.trim());
        }
        this.backoffMillis = backoffMillis;
    }

    private static SimpleClientHttpRequestFactory timeoutFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
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

        for (String model : models) {
            GeminiResponse response = callWithRetry(model, body);
            if (response != null) {
                if (response.candidates() == null || response.candidates().isEmpty()
                        || response.candidates().get(0).content() == null) {
                    throw new BusinessException("The assistant returned no answer.");
                }
                return response.candidates().get(0).content().parts();
            }
        }
        throw new BusinessException("The assistant is unavailable right now. Try again in a moment.");
    }

    /** Returns the answer, or null when this model stayed unavailable (so the next one is tried). */
    private GeminiResponse callWithRetry(String model, Map<String, Object> body) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_MODEL; attempt++) {
            try {
                return http.post()
                        .uri("/models/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(GeminiResponse.class);
            } catch (RestClientResponseException e) {
                if (!isTemporary(e.getStatusCode())) {
                    // 400/401/403/404: retrying would fail the same way (bad key, bad model name...).
                    log.error("Gemini call failed (model {}, status {})", model, e.getStatusCode().value(), e);
                    return null;
                }
                log.warn("Gemini busy (model {}, status {}, attempt {}/{})",
                        model, e.getStatusCode().value(), attempt, MAX_ATTEMPTS_PER_MODEL);
            } catch (RestClientException e) {
                // Timeout or network error: waiting again on the same model would take too long.
                log.warn("Gemini did not answer in time (model {}): {}", model, e.getMessage());
                return null;
            }
            if (attempt < MAX_ATTEMPTS_PER_MODEL) {
                sleep(backoffMillis * attempt); // 1s, then 2s
            }
        }
        return null;
    }

    private static boolean isTemporary(HttpStatusCode status) {
        return status.value() == 429 || status.is5xxServerError();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("The assistant is unavailable right now. Try again in a moment.");
        }
    }

    record GeminiResponse(List<Candidate> candidates) {}
    record Candidate(Content content) {}
    record Content(String role, List<Map<String, Object>> parts) {}
}
