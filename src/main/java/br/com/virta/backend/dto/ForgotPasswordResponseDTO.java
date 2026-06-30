package br.com.virta.backend.dto;

/**
 * Neutral response for a password recovery request. The debugToken is a testing
 * helper (there is no real e-mail delivery) and would be null/absent in production.
 */
public record ForgotPasswordResponseDTO(
        String message,
        String debugToken
) {}
