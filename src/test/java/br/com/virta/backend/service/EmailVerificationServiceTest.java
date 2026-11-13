package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The confirmation link: hashed, 24-hour, single use, and a neutral "resend". */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "ana@virta.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationService service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Ana", EMAIL, "hash"); // new accounts start unconfirmed
    }

    @Test
    void sendsTheLinkAndStoresOnlyTheHashOfTheToken() {
        service.sendVerification(user);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmailVerification(eq(EMAIL), eq("Ana"), token.capture());
        assertThat(user.getEmailVerificationTokenHash())
                .isEqualTo(EmailVerificationService.sha256(token.getValue()))
                .isNotEqualTo(token.getValue());
        assertThat(user.getEmailVerificationExpiresAt())
                .isBetween(LocalDateTime.now().plusHours(23), LocalDateTime.now().plusHours(25));
    }

    @Test
    void aValidLinkActivatesTheAccountAndCannotBeUsedAgain() {
        givenPendingToken("abc", LocalDateTime.now().plusHours(1));

        service.verify("abc");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getEmailVerificationTokenHash()).isNull(); // single use
    }

    @Test
    void anExpiredLinkIsRejected() {
        givenPendingToken("abc", LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> service.verify("abc")).isInstanceOf(BusinessException.class);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void anUnknownLinkIsRejected() {
        when(userRepository.findByEmailVerificationTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify("forged")).isInstanceOf(BusinessException.class);
    }

    @Test
    void resendDoesNothingForAConfirmedAccount() {
        user.setEmailVerified(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.resend(EMAIL);

        verify(emailService, never()).sendEmailVerification(any(), any(), any());
    }

    @Test
    void resendSendsANewLinkForAnUnconfirmedAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.resend(EMAIL);

        verify(emailService).sendEmailVerification(eq(EMAIL), eq("Ana"), anyString());
    }

    private void givenPendingToken(String token, LocalDateTime expiresAt) {
        user.setEmailVerificationTokenHash(EmailVerificationService.sha256(token));
        user.setEmailVerificationExpiresAt(expiresAt);
        when(userRepository.findByEmailVerificationTokenHash(EmailVerificationService.sha256(token)))
                .thenReturn(Optional.of(user));
    }
}
