package br.com.virta.backend.service;

import br.com.virta.backend.dto.GoogleUserInfo;
import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.exception.EmailAlreadyExistsException;
import br.com.virta.backend.exception.EmailNotVerifiedException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final WalletService walletService;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserRepository userRepository,
                       GoogleTokenVerifier googleTokenVerifier,
                       WalletService walletService,
                       JwtService jwtService,
                       TwoFactorService twoFactorService,
                       EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.googleTokenVerifier = googleTokenVerifier;
        this.walletService = walletService;
        this.jwtService = jwtService;
        this.twoFactorService = twoFactorService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public void register(RegisterRequestDTO dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new EmailAlreadyExistsException(dto.email());
        }
        User user = userRepository.save(
                new User(dto.name(), dto.email(), passwordEncoder.encode(dto.password())));
        walletService.createDefaultWallet(user);
        // The account starts inactive: it only works after the link sent by e-mail is opened.
        emailVerificationService.sendVerification(user);
    }

    public User login(LoginRequestDTO dto) {
        User user = userRepository.findByEmail(dto.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(dto.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        // Checked only after the password, so strangers can't learn which accounts are unconfirmed.
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
        return user;
    }


    /**
     * Second step of the login when TOTP is on: the challenge proves the password was
     * right (valid for 5 minutes); the code proves the user has the phone.
     */
    @Transactional
    public User loginWithTotp(String challengeToken, String code) {
        String email;
        try {
            email = jwtService.validateTotpChallenge(challengeToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException(); // forged, expired or not a challenge
        }
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!user.isTotpEnabled() || !twoFactorService.verifyCode(user, code)) {
            throw new BusinessException("Invalid code.");
        }
        return user;
    }

    /**
     * Login/sign-up via Google. Validates the ID token and, on first access,
     * creates the user with a random password (the account is owned by Google).
     */
    @Transactional
    public User loginWithGoogle(String credential) {
        GoogleUserInfo info = googleTokenVerifier.verify(credential);
        return userRepository.findByEmail(info.email())
                .map(existing -> {
                    if (!existing.isEmailVerified()) {
                        // Google proved the e-mail belongs to whoever is signing in now. The password
                        // was set by someone who never confirmed the e-mail — possibly an attacker who
                        // signed up first ("pre-account takeover") — so it is replaced. The owner can
                        // set a new one with "forgot password".
                        existing.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                        EmailVerificationService.markVerified(existing);
                        userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    User created = new User(
                            info.name(),
                            info.email(),
                            passwordEncoder.encode(UUID.randomUUID().toString()));
                    created.setPhoto(info.photo());
                    created.setEmailVerified(true); // Google already verified this e-mail
                    User saved = userRepository.save(created);
                    walletService.createDefaultWallet(saved);
                    return saved;
                });
    }
}
