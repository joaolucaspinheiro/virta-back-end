package br.com.virta.backend.controller;

import br.com.virta.backend.dto.TransactionRequestDTO;
import br.com.virta.backend.dto.TransactionResponseDTO;
import br.com.virta.backend.model.TransactionType;
import br.com.virta.backend.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets/{walletId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponseDTO>> list(@PathVariable Long walletId,
                                                             @RequestParam(required = false) TransactionType type,
                                                             Authentication auth) {
        return ResponseEntity.ok(transactionService.list(auth.getName(), walletId, type));
    }
    @GetMapping(value="/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long walletId, Authentication auth){
        return transactionService.subscribe(auth.getName(), walletId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> get(@PathVariable Long walletId,
                                                      @PathVariable Long id,
                                                      Authentication auth) {
        return ResponseEntity.ok(transactionService.get(auth.getName(), walletId, id));
    }

    @PostMapping
    public ResponseEntity<TransactionResponseDTO> create(@PathVariable Long walletId,
                                                         @RequestBody @Valid TransactionRequestDTO dto,
                                                         Authentication auth) {
        TransactionResponseDTO created = transactionService.create(auth.getName(), walletId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponseDTO> update(@PathVariable Long walletId,
                                                         @PathVariable Long id,
                                                         @RequestBody @Valid TransactionRequestDTO dto,
                                                         Authentication auth) {
        return ResponseEntity.ok(transactionService.update(auth.getName(), walletId, id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long walletId,
                                       @PathVariable Long id,
                                       Authentication auth) {
        transactionService.delete(auth.getName(), walletId, id);
        return ResponseEntity.noContent().build();
    }
}
