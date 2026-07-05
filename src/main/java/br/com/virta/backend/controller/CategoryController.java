package br.com.virta.backend.controller;

import br.com.virta.backend.dto.CategoryRequestDTO;
import br.com.virta.backend.dto.CategoryResponseDTO;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> list(
            @RequestParam(required = false) TransactionType type,
            Authentication auth) {
        return ResponseEntity.ok(categoryService.list(auth.getName(), type));
    }

    @PostMapping
    public ResponseEntity<CategoryResponseDTO> create(@RequestBody @Valid CategoryRequestDTO dto,
                                                      Authentication auth) {
        CategoryResponseDTO created = categoryService.create(auth.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> update(@PathVariable Long id,
                                                      @RequestBody @Valid CategoryRequestDTO dto,
                                                      Authentication auth) {
        return ResponseEntity.ok(categoryService.update(auth.getName(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        categoryService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
