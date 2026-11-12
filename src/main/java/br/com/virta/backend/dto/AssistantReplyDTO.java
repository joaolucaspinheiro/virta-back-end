package br.com.virta.backend.dto;

import java.util.List;

/** The assistant's answer plus the tools that changed data (e.g. "create_transaction"). */
public record AssistantReplyDTO(String reply, List<String> actions) {}