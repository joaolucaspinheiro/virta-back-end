package br.com.virta.backend.repository;

import br.com.virta.backend.model.Transaction;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByWallet(Wallet wallet);
    List<Transaction> findByWalletAndType(Wallet wallet, TransactionType type);
    Optional<Transaction> findByIdAndWallet(Long id, Wallet wallet);
    List<Transaction> findByWalletAndDateBetween(Wallet wallet, LocalDate start, LocalDate end);
}
