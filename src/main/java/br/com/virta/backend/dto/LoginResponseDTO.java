package br.com.virta.backend.dto;

public record LoginResponseDTO(
        String token,
        Long id,
        String nome,
        String email
) {}
