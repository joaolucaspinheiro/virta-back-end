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
    private final EmailService emailService;


    private static final long EXPIRATION_HOURS = 1;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PasswordResetService(EmailService emailService, UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository) {
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
    }

    /**
     * Step 1: if the e-mail exists, generates a token and e-mails the reset link.
     * The client response is neutral regardless, so it never reveals whether the
     * e-mail is registered.
     */
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user ->
                emailService.sendPasswordReset(user.getEmail(), user.getName(), createToken(user)));
    }

    /** Generates and persists a one-hour token for the user (also used by wallet invites). */
    public String createToken(User user) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPIRATION_HOURS);
        tokenRepository.save(new PasswordResetToken(user, token, expiresAt));
        return token;
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
        // The link came by e-mail, so opening it also proves the e-mail (covers wallet invites too).
        EmailVerificationService.markVerified(user);
        userRepository.save(user);

        record.setUsed(true);
        tokenRepository.save(record);
    }
}
