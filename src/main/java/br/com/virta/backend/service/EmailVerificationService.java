package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Confirms that whoever signed up owns the e-mail: the account stays inactive until
 * the link sent to that address is opened. Only a SHA-256 of the link token is stored.
 */
@Service
public class EmailVerificationService {

    private static final long EXPIRATION_HOURS = 24;

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /** Creates a new 24-hour link (an older one stops working) and e-mails it. */
    @Transactional
    public void sendVerification(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        user.setEmailVerificationTokenHash(sha256(token));
        user.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(EXPIRATION_HOURS));
        userRepository.save(user);
        emailService.sendEmailVerification(user.getEmail(), user.getName(), token);
    }

    /** Opens the link: activates the account if the token exists, is not expired and wasn't used. */
    @Transactional
    public void verify(String token) {
        User user = userRepository.findByEmailVerificationTokenHash(sha256(token))
                .filter(u -> u.getEmailVerificationExpiresAt() != null
                        && u.getEmailVerificationExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException("This confirmation link is invalid or has expired."));
        markVerified(user);
        userRepository.save(user);
    }

    /** Neutral on purpose: the answer is the same whether the e-mail exists or is already confirmed. */
    @Transactional
    public void resend(String email) {
        userRepository.findByEmail(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresent(this::sendVerification);
    }

    /** Also used by the Google login and the password reset, which prove the e-mail as well. */
    public static void markVerified(User user) {
        user.setEmailVerified(true);
        user.setEmailVerificationTokenHash(null);
        user.setEmailVerificationExpiresAt(null);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
