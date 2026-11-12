package br.com.virta.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The conversation so far; the front keeps it and sends it on every message (Gemini has no memory). */
public record AssistantChatRequestDTO(
        @NotEmpty @Size(max = 20) List<@Valid AssistantMessageDTO> messages
) {}