package br.com.virta.backend.dto;

import br.com.virta.backend.model.TransactionType;

public record CategoryResponseDTO(
        Long id,
        String name,
        TransactionType type,
        String color,
        String icon
) {}
