package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Turning TOTP on/off and the recovery codes (hashed, single use). */
@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    private static final String EMAIL = "lucas@virta.com";
    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Mock
    private UserRepository userRepository;
    @Mock
    private TotpService totpService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private TwoFactorService twoFactorService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Lucas", EMAIL, "hash");
        user.setTotpSecret(SECRET);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void enablingRequiresAValidCode() {
        when(totpService.verify(SECRET, "000000")).thenReturn(false);

        assertThatThrownBy(() -> twoFactorService.enable(EMAIL, "000000"))
                .isInstanceOf(BusinessException.class);
        assertThat(user.isTotpEnabled()).isFalse();
        verify(emailService, never()).sendSecurityAlert(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void enablingReturnsEightCodesAndStoresOnlyTheirHashes() {
        when(totpService.verify(SECRET, "123456")).thenReturn(true);

        List<String> codes = twoFactorService.enable(EMAIL, "123456");

        assertThat(user.isTotpEnabled()).isTrue();
        assertThat(codes).hasSize(8).allMatch(c -> c.matches("[A-Z2-9]{4}-[A-Z2-9]{4}"));
        // The database must never have the codes in plain text.
        assertThat(user.getTotpRecoveryCodes()).doesNotContain(codes.get(0).replace("-", ""));
    }

    @Test
    void aRecoveryCodeWorksOnlyOnce() {
        when(totpService.verify(SECRET, "123456")).thenReturn(true);
        List<String> codes = twoFactorService.enable(EMAIL, "123456");
        String typedByUser = codes.get(0).toLowerCase().replace("-", ""); // case and dash don't matter

        assertThat(twoFactorService.verifyCode(user, typedByUser)).isTrue();
        assertThat(twoFactorService.verifyCode(user, typedByUser)).isFalse();
        assertThat(twoFactorService.verifyCode(user, codes.get(1))).isTrue(); // the others still work
        // The owner is told every time a recovery code is used (2 uses -> 6 left).
        verify(emailService).sendSecurityAlert(eq(EMAIL), eq("Lucas"), eq("Código de recuperação usado"),
                contains("Restam 6 códigos"));
    }

    @Test
    void disablingClearsTheSecretAndTheCodes() {
        when(totpService.verify(SECRET, "123456")).thenReturn(true);
        twoFactorService.enable(EMAIL, "123456");

        twoFactorService.disable(EMAIL, "123456");

        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
        assertThat(user.getTotpRecoveryCodes()).isNull();
        verify(emailService).sendSecurityAlert(eq(EMAIL), eq("Lucas"),
                eq("Verificação em duas etapas ativada"), anyString());
        verify(emailService).sendSecurityAlert(eq(EMAIL), eq("Lucas"),
                eq("Verificação em duas etapas desativada"), anyString());
    }
}
