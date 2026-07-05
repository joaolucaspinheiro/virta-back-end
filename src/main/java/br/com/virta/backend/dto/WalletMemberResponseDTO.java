package br.com.virta.backend.dto;

import br.com.virta.backend.model.WalletRole;

import java.time.LocalDateTime;

public record WalletMemberResponseDTO(
        Long userId,
        String name,
        String email,
        WalletRole role,
        LocalDateTime joinedAt
) {}
