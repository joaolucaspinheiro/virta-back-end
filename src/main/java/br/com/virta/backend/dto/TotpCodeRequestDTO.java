package br.com.virta.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TotpCodeRequestDTO(
        @NotBlank
        @Pattern(regexp = "\\d{6}|[A-Za-z0-9]{4}-?[A-Za-z0-9]{4}",
                message = "use the 6-digit code from the app or a recovery code")
        String code
) {}