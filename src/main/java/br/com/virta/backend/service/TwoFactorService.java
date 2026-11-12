package br.com.virta.backend.service;

import br.com.virta.backend.dto.TotpSetupResponseDTO;
import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Turns two-factor authentication (TOTP) on and off and checks its codes. */
@Service
public class TwoFactorService {

    private static final int RECOVERY_CODES = 8;
    // No 0/O or 1/I: easy to read and type.
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(UserRepository userRepository, TotpService totpService) {
        this.userRepository = userRepository;
        this.totpService = totpService;
    }

    /** Enabling, step 1: a new secret (not active yet) and the QR code content. */
    @Transactional
    public TotpSetupResponseDTO setup(String email) {
        User user = user(email);
        if (user.isTotpEnabled()) {
            throw new BusinessException("Two-factor authentication is already enabled.");
        }
        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);
        return new TotpSetupResponseDTO(secret, totpService.otpauthUri(secret, user.getEmail()));
    }

    /**
     * Enabling, step 2: the user proves the app is set up by typing a valid code.
     * Returns the recovery codes in plain text — the only time they are ever shown.
     */
    @Transactional
    public List<String> enable(String email, String code) {
        User user = user(email);
        if (user.isTotpEnabled()) {
            throw new BusinessException("Two-factor authentication is already enabled.");
        }
        if (user.getTotpSecret() == null) {
            throw new BusinessException("Start the setup first.");
        }
        if (!totpService.verify(user.getTotpSecret(), code)) {
            throw new BusinessException("Invalid code. Check the time on your phone and try again.");
        }
        List<String> codes = newRecoveryCodes();
        user.setTotpRecoveryCodes(codes.stream()
                .map(c -> passwordEncoder.encode(normalize(c)))
                .collect(Collectors.joining(";")));
        user.setTotpEnabled(true);
        userRepository.save(user);
        return codes;
    }

    /** Turning off also asks for a code: a stolen open session can't remove the protection. */
    @Transactional
    public void disable(String email, String code) {
        User user = user(email);
        if (!user.isTotpEnabled()) {
            throw new BusinessException("Two-factor authentication is not enabled.");
        }
        if (!verifyCode(user, code)) {
            throw new BusinessException("Invalid code.");
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpRecoveryCodes(null);
        userRepository.save(user);
    }

    /**
     * Accepts the 6-digit code from the app or one of the recovery codes.
     * A recovery code is removed once used. Also used by the login (next step).
     */
    @Transactional
    public boolean verifyCode(User user, String code) {
        if (code == null) {
            return false;
        }
        if (code.matches("\\d{6}")) {
            return totpService.verify(user.getTotpSecret(), code);
        }
        if (user.getTotpRecoveryCodes() == null || user.getTotpRecoveryCodes().isBlank()) {
            return false;
        }
        String typed = normalize(code);
        List<String> hashes = new ArrayList<>(Arrays.asList(user.getTotpRecoveryCodes().split(";")));
        for (String hash : hashes) {
            if (passwordEncoder.matches(typed, hash)) {
                hashes.remove(hash);
                user.setTotpRecoveryCodes(String.join(";", hashes));
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }

    private List<String> newRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    code.append('-');
                }
                code.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /** "k7qm-2xpa", "K7QM2XPA" and "K7QM-2XPA" are the same code. */
    private static String normalize(String code) {
        return code.replace("-", "").replace(" ", "").toUpperCase();
    }

    private User user(String email) {
        return userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    }
}