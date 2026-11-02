package br.com.virta.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Sends transactional e-mail through Brevo's HTTPS API. SMTP is not used because
 * free hosting plans (Render) block the SMTP ports.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RestClient http = RestClient.create("https://api.brevo.com/v3");
    private final String apiKey;
    private final String from;
    private final String frontendUrl;

    public EmailService(@Value("${brevo.api-key:}") String apiKey,
                        @Value("${mail.from:}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.apiKey = apiKey;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    /** Runs in another thread, so the HTTP response never waits on (or reveals) the e-mail. */
    @Async
    public void sendPasswordReset(String to, String token) {
        String link = frontendUrl + "/redefinir-senha/" + token;
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "Virta", "email", from),
                "to", List.of(Map.of("email", to)),
                "subject", "Virta - Redefinição de senha",
                "textContent", "Para definir uma nova senha, acesse o link abaixo (válido por 1 hora):\n\n"
                        + link
                        + "\n\nSe você não pediu isso, ignore este e-mail.");
        try {
            http.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send password reset e-mail to {}", to, e);
        }
    }
}
