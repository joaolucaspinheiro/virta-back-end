package br.com.virta.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** Corpo da requisição de login com Google: o ID token (credential) do GIS. */
public record GoogleLoginRequestDTO(
        @NotBlank String credential
) {}
