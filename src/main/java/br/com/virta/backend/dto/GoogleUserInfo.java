package br.com.virta.backend.dto;

/** Dados extraídos do ID token do Google já validado. */
public record GoogleUserInfo(
        String email,
        String nome,
        String foto
) {}
