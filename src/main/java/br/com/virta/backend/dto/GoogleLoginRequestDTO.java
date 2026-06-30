package br.com.virta.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of the Google login request: the GIS ID token (credential). */
public record GoogleLoginRequestDTO(
        @NotBlank String credential
) {}
