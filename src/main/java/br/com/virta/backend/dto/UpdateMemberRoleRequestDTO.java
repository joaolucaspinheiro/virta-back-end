package br.com.virta.backend.dto;

import br.com.virta.backend.model.WalletRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequestDTO(
        @NotNull WalletRole role
) {}
