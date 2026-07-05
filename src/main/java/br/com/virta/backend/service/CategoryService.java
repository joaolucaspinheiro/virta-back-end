package br.com.virta.backend.service;

import br.com.virta.backend.dto.CategoryRequestDTO;
import br.com.virta.backend.dto.CategoryResponseDTO;
import br.com.virta.backend.exception.ResourceNotFoundException;
import br.com.virta.backend.model.Category;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.model.User;
import br.com.virta.backend.repository.CategoryRepository;
import br.com.virta.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponseDTO> list(String email, TransactionType type) {
        User user = currentUser(email);
        List<Category> categories = (type == null)
                ? categoryRepository.findByUser(user)
                : categoryRepository.findByUserAndType(user, type);
        return categories.stream().map(this::toDto).toList();
    }

    @Transactional
    public CategoryResponseDTO create(String email, CategoryRequestDTO dto) {
        User user = currentUser(email);
        Category category = categoryRepository.save(
                new Category(user, dto.name(), dto.type(), dto.color(), dto.icon()));
        return toDto(category);
    }

    @Transactional
    public CategoryResponseDTO update(String email, Long id, CategoryRequestDTO dto) {
        Category category = ownedCategory(email, id);
        category.setName(dto.name());
        category.setType(dto.type());
        category.setColor(dto.color());
        category.setIcon(dto.icon());
        categoryRepository.save(category);
        return toDto(category);
    }

    @Transactional
    public void delete(String email, Long id) {
        Category category = ownedCategory(email, id);
        // TODO: once transactions exist, block deletion when linked (422).
        categoryRepository.delete(category);
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private Category ownedCategory(String email, Long id) {
        return categoryRepository.findByIdAndUser(id, currentUser(email))
                .orElseThrow(() -> new ResourceNotFoundException("Category not found."));
    }

    private CategoryResponseDTO toDto(Category c) {
        return new CategoryResponseDTO(c.getId(), c.getName(), c.getType(), c.getColor(), c.getIcon());
    }
}
