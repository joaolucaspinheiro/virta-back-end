package br.com.virta.backend.dto;

import java.util.List;

public record TotpEnableResponseDTO(String message, List<String> recoveryCodes) {}