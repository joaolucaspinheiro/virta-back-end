package br.com.virta.backend.service;

import br.com.virta.backend.model.WalletRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional e-mail through Brevo's HTTPS API. SMTP is not used because
 * free hosting plans (Render) block the SMTP ports.
 *
 * Bodies come from HTML templates in resources/templates/email, with {{key}}
 * placeholders. Every value is HTML-escaped before it goes into the template,
 * because names (user, wallet) are typed by users.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final RestClient http = RestClient.create("https://api.brevo.com/v3");
    private final String apiKey;
    private final String from;
    private final String frontendUrl;
    private final String resetTemplate;
    private final String inviteTemplate;

    public EmailService(@Value("${brevo.api-key:}") String apiKey,
                        @Value("${mail.from:}") String from,
                        @Value("${app.frontend-url}") String frontendUrl) {
        this.apiKey = apiKey;
        this.from = from;
        this.frontendUrl = frontendUrl;
        this.resetTemplate = loadTemplate("password-reset.html");
        this.inviteTemplate = loadTemplate("invite.html");
    }

    /** Runs in another thread, so the HTTP response never waits on (or reveals) the e-mail. */
    @Async
    public void sendPasswordReset(String to, String name, String token) {
        String link = resetLink(token);
        String html = render(resetTemplate, Map.of(
                "name", name,
                "link", link,
                "logoUrl", logoUrl()));
        String text = "Olá, " + name + ",\n\n"
                + "Para definir uma nova senha no Virta, acesse o link abaixo (válido por 1 hora):\n\n"
                + link
                + "\n\nNão foi você? Pode ignorar este e-mail; sua senha continua a mesma.";
        send(to, "Virta - Redefinição de senha", html, text);
    }

    @Async
    public void sendInvite(String to, String inviterName, String walletName, WalletRole role, String token) {
        String link = resetLink(token);
        String roleLabel = roleLabel(role);
        String html = render(inviteTemplate, Map.of(
                "inviter", inviterName,
                "wallet", walletName,
                "role", roleLabel,
                "link", link,
                "logoUrl", logoUrl()));
        String text = inviterName + " convidou você para a carteira \"" + walletName + "\" no Virta"
                + " (papel: " + roleLabel + ").\n\n"
                + "Para entrar, crie sua senha pelo link abaixo (válido por 1 hora):\n\n"
                + link
                + "\n\nNão conhece quem enviou? Pode ignorar este e-mail.";
        send(to, "Virta - " + inviterName + " convidou você para uma carteira", html, text);
    }

    private void send(String to, String subject, String html, String text) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", "Virta", "email", from),
                "to", List.of(Map.of("email", to)),
                "subject", subject,
                "htmlContent", html,
                "textContent", text);
        try {
            http.post()
                    .uri("/smtp/email")
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to send e-mail \"{}\" to {}", subject, mask(to), e);
        }
    }

    private String render(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", HtmlUtils.htmlEscape(entry.getValue()));
        }
        return result;
    }

    private String resetLink(String token) {
        return frontendUrl + "/redefinir-senha/" + token;
    }

    /** PNG version of the wave logo: SVG is not displayed by Gmail/Outlook. */
    private String logoUrl() {
        return frontendUrl + "/pwa-192x192.png";
    }

    private static String roleLabel(WalletRole role) {
        return switch (role) {
            case OWNER -> "Dono";
            case EDITOR -> "Editor";
            case VIEWER -> "Leitor";
        };
    }

    /** "lucas@gmail.com" -> "l***@gmail.com": keeps personal data out of the logs (LGPD). */
    private static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String loadTemplate(String name) {
        try {
            return new ClassPathResource("templates/email/" + name)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("E-mail template not found: " + name, e);
        }
    }
}
