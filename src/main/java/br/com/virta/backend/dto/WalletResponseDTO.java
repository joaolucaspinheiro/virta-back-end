package br.com.virta.backend.dto;

import br.com.virta.backend.model.WalletRole;

import java.time.LocalDateTime;

/** Wallet view including the requesting user's role in it. */
public record WalletResponseDTO(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        WalletRole role,
        String ownerName
) {}
