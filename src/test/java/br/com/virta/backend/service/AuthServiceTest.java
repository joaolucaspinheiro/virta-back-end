package br.com.virta.backend.service;

import br.com.virta.backend.dto.GoogleUserInfo;
import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.exception.EmailNotVerifiedException;
import br.com.virta.backend.exception.InvalidCredentialsException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Sign-up, login and Google login with e-mail confirmation. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "ana@virta.com";
    private static final String PASSWORD = "Senha@123";

    @Mock
    private UserRepository userRepository;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private WalletService walletService;
    @Mock
    private JwtService jwtService;
    @Mock
    private TwoFactorService twoFactorService;
    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthService authService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void signUpCreatesAnInactiveAccountAndSendsTheConfirmation() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequestDTO("Ana", EMAIL, PASSWORD));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(emailVerificationService).sendVerification(saved.capture());
        assertThat(saved.getValue().isEmailVerified()).isFalse();
    }

    @Test
    void loginIsBlockedUntilTheEmailIsConfirmed() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO(EMAIL, PASSWORD)))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    @Test
    void aWrongPasswordDoesNotRevealThatTheAccountIsUnconfirmed() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));

        assertThatThrownBy(() -> authService.login(new LoginRequestDTO(EMAIL, "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void aConfirmedAccountLogsIn() {
        User confirmed = user(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(confirmed));

        assertThat(authService.login(new LoginRequestDTO(EMAIL, PASSWORD))).isSameAs(confirmed);
    }

    @Test
    void googleLoginOnAnUnconfirmedAccountConfirmsItAndDiscardsThePassword() {
        // Someone signed up with this e-mail but never confirmed it (maybe an attacker).
        User unconfirmed = user(false);
        when(googleTokenVerifier.verify("google-token")).thenReturn(new GoogleUserInfo(EMAIL, "Ana", null));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unconfirmed));

        User loggedIn = authService.loginWithGoogle("google-token");

        assertThat(loggedIn.isEmailVerified()).isTrue();
        // The password chosen by whoever signed up first no longer works.
        assertThat(encoder.matches(PASSWORD, loggedIn.getPasswordHash())).isFalse();
    }

    @Test
    void newGoogleAccountsStartConfirmed() {
        when(googleTokenVerifier.verify("google-token")).thenReturn(new GoogleUserInfo(EMAIL, "Ana", null));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(authService.loginWithGoogle("google-token").isEmailVerified()).isTrue();
    }

    private User user(boolean verified) {
        User user = new User("Ana", EMAIL, encoder.encode(PASSWORD));
        user.setEmailVerified(verified);
        return user;
    }
}
