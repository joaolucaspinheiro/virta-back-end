package br.com.virta.backend.service;

import br.com.virta.backend.dto.DashboardSummaryResponseDTO;
import br.com.virta.backend.dto.DashboardSummaryResponseDTO.CategorySummary;
import br.com.virta.backend.dto.DashboardSummaryResponseDTO.MonthSummary;
import br.com.virta.backend.dto.TransactionRequestDTO;
import br.com.virta.backend.dto.TransactionResponseDTO;
import br.com.virta.backend.exception.ResourceNotFoundException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final WalletMemberRepository memberRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              WalletRepository walletRepository,
                              WalletMemberRepository memberRepository,
                              CategoryRepository categoryRepository,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.memberRepository = memberRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionResponseDTO> list(String email, Long walletId, TransactionType type) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        membershipOrThrow(wallet, user); // any member (OWNER/EDITOR/VIEWER) can read
        List<Transaction> transactions = (type == null)
                ? transactionRepository.findByWallet(wallet)
                : transactionRepository.findByWalletAndType(wallet, type);
        return transactions.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponseDTO get(String email, Long walletId, Long id) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        membershipOrThrow(wallet, user);
        return toDto(transactionOrThrow(id, wallet));
    }

    @Transactional
    public TransactionResponseDTO create(String email, Long walletId, TransactionRequestDTO dto) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireWriter(membershipOrThrow(wallet, user));
        Category category = resolveCategory(dto.categoryId(), user);
        Transaction transaction = transactionRepository.save(new Transaction(
                wallet, category, user, dto.type(), dto.amount(), dto.description(), dto.date()));
        return toDto(transaction);
    }

    @Transactional
    public TransactionResponseDTO update(String email, Long walletId, Long id, TransactionRequestDTO dto) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireWriter(membershipOrThrow(wallet, user));
        Transaction transaction = transactionOrThrow(id, wallet);
        transaction.setType(dto.type());
        transaction.setAmount(dto.amount());
        transaction.setDescription(dto.description());
        transaction.setDate(dto.date());
        transaction.setCategory(resolveCategory(dto.categoryId(), user));
        transactionRepository.save(transaction);
        return toDto(transaction);
    }

    @Transactional
    public void delete(String email, Long walletId, Long id) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        requireWriter(membershipOrThrow(wallet, user));
        transactionRepository.delete(transactionOrThrow(id, wallet));
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponseDTO summary(String email, Long walletId,
                                               LocalDate startDate, LocalDate endDate) {
        User user = currentUser(email);
        Wallet wallet = walletOrThrow(walletId);
        membershipOrThrow(wallet, user); // any member can see the summary

        List<Transaction> txs = (startDate != null && endDate != null)
                ? transactionRepository.findByWalletAndDateBetween(wallet, startDate, endDate)
                : transactionRepository.findByWallet(wallet);

        BigDecimal totalIncome = sumByType(txs, TransactionType.INCOME);
        BigDecimal totalExpense = sumByType(txs, TransactionType.EXPENSE);

        // Total per category (uncategorized transactions are left out of the breakdown).
        List<CategorySummary> byCategory = txs.stream()
                .filter(t -> t.getCategory() != null)
                .collect(Collectors.groupingBy(t -> t.getCategory().getId(),
                        LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new CategorySummary(
                        e.getKey(),
                        e.getValue().get(0).getCategory().getName(),
                        e.getValue().get(0).getCategory().getColor(),
                        sumAll(e.getValue())))
                .toList();

        // Income vs. expense grouped by month (YYYY-MM), sorted chronologically.
        List<MonthSummary> byMonth = txs.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getDate()),
                        TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> new MonthSummary(
                        e.getKey().toString(),
                        sumByType(e.getValue(), TransactionType.INCOME),
                        sumByType(e.getValue(), TransactionType.EXPENSE)))
                .toList();

        return new DashboardSummaryResponseDTO(
                totalIncome,
                totalExpense,
                totalIncome.subtract(totalExpense),
                txs.size(),
                byCategory,
                byMonth);
    }

    private BigDecimal sumByType(List<Transaction> txs, TransactionType type) {
        return txs.stream()
                .filter(t -> t.getType() == type)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumAll(List<Transaction> txs) {
        return txs.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ----- helpers -----

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private Wallet walletOrThrow(Long walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found."));
    }

    private WalletMember membershipOrThrow(Wallet wallet, User user) {
        return memberRepository.findByWalletAndUser(wallet, user)
                .orElseThrow(() -> new AccessDeniedException("You are not a member of this wallet."));
    }

    private Transaction transactionOrThrow(Long id, Wallet wallet) {
        return transactionRepository.findByIdAndWallet(id, wallet)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found."));
    }

    /** A category is optional; when given, it must belong to the current user. */
    private Category resolveCategory(Long categoryId, User user) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findByIdAndUser(categoryId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
    }

    /** OWNER and EDITOR can write; VIEWER is read-only. */
    private void requireWriter(WalletMember membership) {
        if (membership.getRole() == WalletRole.VIEWER) {
            throw new AccessDeniedException("Viewers cannot modify transactions in this wallet.");
        }
    }

    private TransactionResponseDTO toDto(Transaction t) {
        return new TransactionResponseDTO(
                t.getId(),
                t.getType(),
                t.getAmount(),
                t.getDescription(),
                t.getDate(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getName() : null,
                t.getCategory() != null ? t.getCategory().getColor() : null,
                t.getCreatedBy().getName(),
                t.getCreatedAt());
    }
}
