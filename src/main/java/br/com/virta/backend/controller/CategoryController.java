package br.com.virta.backend.controller;

import br.com.virta.backend.dto.CategoryRequestDTO;
import br.com.virta.backend.dto.CategoryResponseDTO;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "5. Categorias", description = "Categorias de receita e despesa do usuário.")
@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Minhas categorias", description = "Filtro opcional por tipo (INCOME ou EXPENSE).")
    @GetMapping
    public ResponseEntity<List<CategoryResponseDTO>> list(
            @RequestParam(required = false) TransactionType type,
            Authentication auth) {
        return ResponseEntity.ok(categoryService.list(auth.getName(), type));
    }

    @Operation(summary = "Criar categoria")
    @PostMapping
    public ResponseEntity<CategoryResponseDTO> create(@RequestBody @Valid CategoryRequestDTO dto,
                                                      Authentication auth) {
        CategoryResponseDTO created = categoryService.create(auth.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Editar categoria")
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDTO> update(@PathVariable Long id,
                                                      @RequestBody @Valid CategoryRequestDTO dto,
                                                      Authentication auth) {
        return ResponseEntity.ok(categoryService.update(auth.getName(), id, dto));
    }

    @Operation(summary = "Excluir categoria")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        categoryService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
