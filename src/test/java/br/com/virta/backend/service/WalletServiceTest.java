package br.com.virta.backend.service;

import br.com.virta.backend.exception.BusinessException;
import br.com.virta.backend.model.User;
import br.com.virta.backend.model.Wallet;
import br.com.virta.backend.model.WalletMember;
import br.com.virta.backend.model.WalletRole;
import br.com.virta.backend.repository.UserRepository;
import br.com.virta.backend.repository.WalletMemberRepository;
import br.com.virta.backend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the "default wallet" rules: created on sign-up, and the last owned wallet can't be deleted. */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final String EMAIL = "lucas@virta.com";
    private static final Long WALLET_ID = 1L;

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private WalletService walletService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User("Lucas", EMAIL, "hash");
        wallet = new Wallet(user, "Minha carteira", null);
    }

    @Test
    void createDefaultWalletSavesTheWalletWithTheUserAsOwner() {
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        walletService.createDefaultWallet(user);

        ArgumentCaptor<Wallet> savedWallet = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(savedWallet.capture());
        assertThat(savedWallet.getValue().getName()).isEqualTo("Minha carteira");
        assertThat(savedWallet.getValue().getOwner()).isSameAs(user);

        ArgumentCaptor<WalletMember> savedMember = ArgumentCaptor.forClass(WalletMember.class);
        verify(memberRepository).save(savedMember.capture());
        assertThat(savedMember.getValue().getRole()).isEqualTo(WalletRole.OWNER);
        assertThat(savedMember.getValue().getUser()).isSameAs(user);
    }

    @Test
    void ownerCannotDeleteTheirOnlyWallet() {
        givenMembership(WalletRole.OWNER);
        when(walletRepository.countByOwner(user)).thenReturn(1L);

        assertThatThrownBy(() -> walletService.delete(EMAIL, WALLET_ID))
                .isInstanceOf(BusinessException.class);
        verify(walletRepository, never()).delete(any());
    }

    @Test
    void ownerCanDeleteAWalletWhenTheyOwnAnother() {
        givenMembership(WalletRole.OWNER);
        when(walletRepository.countByOwner(user)).thenReturn(2L);

        walletService.delete(EMAIL, WALLET_ID);

        verify(walletRepository).delete(wallet);
    }

    @Test
    void nonOwnerCannotDeleteAndDoesNotLearnHowManyWalletsTheOwnerHas() {
        givenMembership(WalletRole.EDITOR);

        assertThatThrownBy(() -> walletService.delete(EMAIL, WALLET_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(walletRepository, never()).countByOwner(any());
        verify(walletRepository, never()).delete(any());
    }

    private void givenMembership(WalletRole role) {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(memberRepository.findByWalletAndUser(wallet, user))
                .thenReturn(Optional.of(new WalletMember(wallet, user, role)));
    }
}
