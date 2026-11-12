package br.com.virta.backend.dto;

public record TotpSetupResponseDTO(String secret, String otpauthUri) {}