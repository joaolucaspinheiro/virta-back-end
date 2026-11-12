package br.com.virta.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One chat message. role is "user" (the person) or "model" (the assistant). */
public record AssistantMessageDTO(
        @NotBlank @Pattern(regexp = "user|model", message = "role must be user or model") String role,
        @NotBlank @Size(max = 2000) String text
) {}