package br.com.virta.backend.dto;

import br.com.virta.backend.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponseDTO(
        Long id,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate date,
        Long categoryId,
        String categoryName,
        String categoryColor,
        String createdByName,
        LocalDateTime createdAt
) {}
