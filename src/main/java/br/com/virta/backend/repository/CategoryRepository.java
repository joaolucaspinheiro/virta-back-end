package br.com.virta.backend.repository;

import br.com.virta.backend.model.Category;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUser(User user);
    List<Category> findByUserAndType(User user, TransactionType type);
    Optional<Category> findByIdAndUser(Long id, User user);
}
