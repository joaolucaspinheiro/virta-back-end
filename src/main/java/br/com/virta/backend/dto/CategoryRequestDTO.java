package br.com.virta.backend.dto;

import br.com.virta.backend.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequestDTO(
        @NotBlank @Size(max = 80) String name,
        @NotNull TransactionType type,
        @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "color must be a hex like #RRGGBB")
        String color,
        String icon
) {}
