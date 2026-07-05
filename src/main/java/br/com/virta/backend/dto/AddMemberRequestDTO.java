package br.com.virta.backend.dto;

import br.com.virta.backend.model.WalletRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequestDTO(
        @NotBlank @Email String email,
        @NotNull WalletRole role
) {}
