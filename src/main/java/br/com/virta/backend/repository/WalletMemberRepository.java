package br.com.virta.backend.repository;

import br.com.virta.backend.model.User;
import br.com.virta.backend.model.Wallet;
import br.com.virta.backend.model.WalletMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletMemberRepository extends JpaRepository<WalletMember, Long> {
    List<WalletMember> findByUser(User user);
    List<WalletMember> findByWallet(Wallet wallet);
    Optional<WalletMember> findByWalletAndUser(Wallet wallet, User user);
    Optional<WalletMember> findByWalletAndUserId(Wallet wallet, Long userId);
    boolean existsByWalletAndUser(Wallet wallet, User user);
}
