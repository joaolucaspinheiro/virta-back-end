package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Retry, fallback and give-up rules of the Gemini client, against a fake Gemini server. */
class GeminiClientTest {

    private static final String BASE = "https://gemini.test";
    private static final String MAIN = BASE + "/models/main-model:generateContent";
    private static final String BACKUP = BASE + "/models/backup-model:generateContent";
    private static final String ANSWER = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Oi!"}]}}]}
            """;

    private MockRestServiceServer server;

    private GeminiClient client(String fallbackModel) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        return new GeminiClient(builder, "test-key", "main-model", fallbackModel, 0);
    }

    private List<Map<String, Object>> ask(GeminiClient client) {
        return client.generate("instructions", List.of(), List.of());
    }

    @Test
    void retriesTheSameModelWhenGeminiIsBusy() {
        GeminiClient client = client("");
        server.expect(requestTo(MAIN)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(MAIN)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        assertThat(ask(client)).containsExactly(Map.of("text", "Oi!"));
        server.verify();
    }

    @Test
    void switchesToTheFallbackModelWhenTheMainOneStaysBusy() {
        GeminiClient client = client("backup-model");
        server.expect(times(GeminiClient.MAX_ATTEMPTS_PER_MODEL), requestTo(MAIN))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(BACKUP)).andRespond(withSuccess(ANSWER, MediaType.APPLICATION_JSON));

        assertThat(ask(client)).containsExactly(Map.of("text", "Oi!"));
        server.verify();
    }

    @Test
    void doesNotRetryErrorsThatWouldRepeat() {
        GeminiClient client = client("");
        server.expect(times(1), requestTo(MAIN)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> ask(client))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unavailable");
        server.verify();
    }

    @Test
    void givesUpWithAFriendlyMessageWhenEveryModelIsBusy() {
        GeminiClient client = client("backup-model");
        server.expect(times(GeminiClient.MAX_ATTEMPTS_PER_MODEL), requestTo(MAIN))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(times(GeminiClient.MAX_ATTEMPTS_PER_MODEL), requestTo(BACKUP))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> ask(client))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("unavailable");
        server.verify();
    }
}
