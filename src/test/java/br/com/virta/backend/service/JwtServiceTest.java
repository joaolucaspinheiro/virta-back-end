package br.com.virta.backend.service;

import br.com.virta.backend.model.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The key security rule of the two-step login: the ticket returned after the password
 * (TOTP challenge) must never work as an access token, and vice versa.
 */
class JwtServiceTest {

    // Test-only key (Base64 of 33 bytes); the real one comes from api.security.token.secret.
    private static final String TEST_KEY = "dmlydGEtdGVzdC1rZXktMDEyMzQ1Njc4OWFiY2RlZiEh";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_KEY);
        user = new User("Lucas", "lucas@virta.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void accessTokenIsAcceptedByTheApi() {
        String token = jwtService.generateToken(user);

        assertThat(jwtService.validateToken(token)).isEqualTo("lucas@virta.com");
    }

    @Test
    void totpChallengeIsRejectedByTheApi() {
        String challenge = jwtService.generateTotpChallenge(user);

        // Knowing only the password must not be enough to call the API.
        assertThatThrownBy(() -> jwtService.validateToken(challenge))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void totpChallengeIsAcceptedOnlyByTheCodeStep() {
        String challenge = jwtService.generateTotpChallenge(user);

        assertThat(jwtService.validateTotpChallenge(challenge)).isEqualTo("lucas@virta.com");
    }

    @Test
    void accessTokenIsNotAValidChallenge() {
        String token = jwtService.generateToken(user);

        assertThatThrownBy(() -> jwtService.validateTotpChallenge(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> jwtService.validateToken(tampered))
                .isInstanceOf(JwtException.class);
    }
}
