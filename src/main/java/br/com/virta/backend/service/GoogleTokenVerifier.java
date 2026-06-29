package br.com.virta.backend.service;

import br.com.virta.backend.dto.GoogleUserInfo;
import br.com.virta.backend.exception.InvalidGoogleTokenException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Valida o ID token do Google consultando o endpoint público tokeninfo.
 * O Google verifica assinatura e expiração; aqui conferimos o destinatário
 * (aud == nosso Client ID) e se o e-mail foi verificado.
 */
@Service
public class GoogleTokenVerifier {

    private static final String TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    private final RestClient restClient = RestClient.create();
    private final String clientId;

    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    public GoogleUserInfo verify(String idToken) {
        TokenInfo info;
        try {
            info = restClient.get()
                    .uri(TOKEN_INFO_URL, idToken)
                    .retrieve()
                    .body(TokenInfo.class);
        } catch (RestClientException e) {
            // Token inválido/expirado faz o Google responder com erro HTTP.
            throw new InvalidGoogleTokenException();
        }

        if (info == null || info.email() == null) {
            throw new InvalidGoogleTokenException();
        }
        if (clientId != null && !clientId.isBlank() && !clientId.equals(info.aud())) {
            throw new InvalidGoogleTokenException();
        }
        if (!"true".equals(info.emailVerified())) {
            throw new InvalidGoogleTokenException();
        }

        String nome = info.name() != null ? info.name() : info.email();
        return new GoogleUserInfo(info.email(), nome, info.picture());
    }

    /** Subconjunto da resposta do tokeninfo que nos interessa. */
    private record TokenInfo(
            String aud,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String name,
            String picture
    ) {}
}
