package br.com.virta.backend.dto;

/** Data extracted from a validated Google ID token. */
public record GoogleUserInfo(
        String email,
        String name,
        String photo
) {}
