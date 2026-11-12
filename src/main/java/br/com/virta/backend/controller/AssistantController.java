package br.com.virta.backend.controller;

import br.com.virta.backend.dto.AssistantChatRequestDTO;
import br.com.virta.backend.dto.AssistantReplyDTO;
import br.com.virta.backend.service.AssistantRateLimiter;
import br.com.virta.backend.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}/assistant")
public class AssistantController {

    private final AssistantService assistantService;
    private final AssistantRateLimiter rateLimiter;

    public AssistantController(AssistantService assistantService, AssistantRateLimiter rateLimiter) {
        this.assistantService = assistantService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<AssistantReplyDTO> chat(@PathVariable Long walletId,
                                                  @RequestBody @Valid AssistantChatRequestDTO dto,
                                                  Authentication auth) {
        rateLimiter.check(auth.getName());
        return ResponseEntity.ok(assistantService.chat(auth.getName(), walletId, dto.messages()));
    }
}