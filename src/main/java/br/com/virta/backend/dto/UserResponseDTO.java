package br.com.virta.backend.dto;

import java.time.LocalDateTime;

public record UserResponseDTO(
        Long id,
        String name,
        String email,
        String photo,
        LocalDateTime createdAt
) {}
