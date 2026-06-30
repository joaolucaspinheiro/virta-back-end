package br.com.virta.backend.service;

import br.com.virta.backend.exception.InvalidResetTokenException;
import br.com.virta.backend.model.PasswordResetToken;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.PasswordResetTokenRepository;
import br.com.virta.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final long EXPIRATION_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    /**
     * Step 1: generates and persists a token if the e-mail exists.
     * Returns the token (for testing/debug only) or null — the client response
     * is neutral regardless, so it never reveals whether the e-mail is registered.
     */
    public String requestReset(String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    String token = UUID.randomUUID().toString();
                    LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPIRATION_HOURS);
                    tokenRepository.save(new PasswordResetToken(user, token, expiresAt));
                    return token;
                })
                .orElse(null);
    }

    /** Step 2: validates the token and changes the user's password. */
    public void reset(String token, String newPassword) {
        PasswordResetToken record = tokenRepository.findByToken(token)
                .orElseThrow(InvalidResetTokenException::new);

        if (record.isUsed() || record.isExpired()) {
            throw new InvalidResetTokenException();
        }

        User user = record.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        record.setUsed(true);
        tokenRepository.save(record);
    }
}
