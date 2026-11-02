package br.com.virta.backend.service;

import br.com.virta.backend.dto.DashboardSummaryResponseDTO;
import br.com.virta.backend.dto.TransactionRequestDTO;
import br.com.virta.backend.model.Category;
import br.com.virta.backend.model.Transaction;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.model.User;
import br.com.virta.backend.model.Wallet;
import br.com.virta.backend.model.WalletMember;
import br.com.virta.backend.model.WalletRole;
import br.com.virta.backend.repository.CategoryRepository;
import br.com.virta.backend.repository.TransactionRepository;
import br.com.virta.backend.repository.UserRepository;
import br.com.virta.backend.repository.WalletMemberRepository;
import br.com.virta.backend.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for access control by role and for the summary filters. */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String EMAIL = "lucas@virta.com";
    private static final Long WALLET_ID = 1L;

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private WalletMemberRepository memberRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletEventService walletEventService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TransactionService transactionService;

    private User user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User("Lucas", EMAIL, "hash");
        wallet = new Wallet(user, "Casa", null);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));
    }

    @Test
    void viewerCannotCreateTransactions() {
        givenRole(WalletRole.VIEWER);
        TransactionRequestDTO dto = new TransactionRequestDTO(
                TransactionType.EXPENSE, new BigDecimal("10.00"), "Café", LocalDate.of(2026, 11, 2), null);

        assertThatThrownBy(() -> transactionService.create(EMAIL, WALLET_ID, dto))
                .isInstanceOf(AccessDeniedException.class);
        verify(transactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void nonMemberCannotSeeTheWalletTransactions() {
        when(memberRepository.findByWalletAndUser(wallet, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.list(EMAIL, WALLET_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void summaryAppliesTypeFilter() {
        givenRole(WalletRole.OWNER);
        givenTransactions();

        DashboardSummaryResponseDTO summary =
                transactionService.summary(EMAIL, WALLET_ID, null, null, null, TransactionType.EXPENSE);

        assertThat(summary.totalIncome()).isEqualByComparingTo("0");
        assertThat(summary.totalExpense()).isEqualByComparingTo("280");
        assertThat(summary.transactionCount()).isEqualTo(3);
    }

    @Test
    void summaryAppliesCategoryFilterAndLeavesUncategorizedOut() {
        givenRole(WalletRole.VIEWER); // viewers can read
        givenTransactions();

        DashboardSummaryResponseDTO summary =
                transactionService.summary(EMAIL, WALLET_ID, null, null, List.of(2L, 3L), null);

        assertThat(summary.totalExpense()).isEqualByComparingTo("250");
        assertThat(summary.transactionCount()).isEqualTo(2);
        assertThat(summary.byCategory()).hasSize(2);
    }

    private void givenRole(WalletRole role) {
        when(memberRepository.findByWalletAndUser(wallet, user))
                .thenReturn(Optional.of(new WalletMember(wallet, user, role)));
    }

    /** Salary 1000 (income), food 200, transport 50 and an uncategorized 30 (expenses). */
    private void givenTransactions() {
        Category salary = category(1L, "Salário", TransactionType.INCOME);
        Category food = category(2L, "Alimentação", TransactionType.EXPENSE);
        Category transport = category(3L, "Transporte", TransactionType.EXPENSE);
        LocalDate day = LocalDate.of(2026, 11, 2);
        when(transactionRepository.findByWallet(wallet)).thenReturn(List.of(
                new Transaction(wallet, salary, user, TransactionType.INCOME, new BigDecimal("1000"), "Salário", day),
                new Transaction(wallet, food, user, TransactionType.EXPENSE, new BigDecimal("200"), "Mercado", day),
                new Transaction(wallet, transport, user, TransactionType.EXPENSE, new BigDecimal("50"), "Uber", day),
                new Transaction(wallet, null, user, TransactionType.EXPENSE, new BigDecimal("30"), "Avulso", day)));
    }

    private Category category(Long id, String name, TransactionType type) {
        Category category = new Category(user, name, type, "#000000", null);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }
}
