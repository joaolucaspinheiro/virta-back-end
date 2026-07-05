package br.com.virta.backend.controller;

import br.com.virta.backend.dto.AddMemberRequestDTO;
import br.com.virta.backend.dto.UpdateMemberRoleRequestDTO;
import br.com.virta.backend.dto.WalletMemberResponseDTO;
import br.com.virta.backend.dto.WalletRequestDTO;
import br.com.virta.backend.dto.WalletResponseDTO;
import br.com.virta.backend.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    public ResponseEntity<List<WalletResponseDTO>> list(Authentication auth) {
        return ResponseEntity.ok(walletService.list(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<WalletResponseDTO> create(@RequestBody @Valid WalletRequestDTO dto,
                                                    Authentication auth) {
        WalletResponseDTO created = walletService.create(auth.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponseDTO> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(walletService.get(auth.getName(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WalletResponseDTO> update(@PathVariable Long id,
                                                    @RequestBody @Valid WalletRequestDTO dto,
                                                    Authentication auth) {
        return ResponseEntity.ok(walletService.update(auth.getName(), id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        walletService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<WalletMemberResponseDTO>> listMembers(@PathVariable Long id,
                                                                     Authentication auth) {
        return ResponseEntity.ok(walletService.listMembers(auth.getName(), id));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<WalletMemberResponseDTO> addMember(@PathVariable Long id,
                                                             @RequestBody @Valid AddMemberRequestDTO dto,
                                                             Authentication auth) {
        WalletMemberResponseDTO member = walletService.addMember(auth.getName(), id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(member);
    }

    @PatchMapping("/{id}/members/{userId}")
    public ResponseEntity<WalletMemberResponseDTO> updateMemberRole(@PathVariable Long id,
                                                                    @PathVariable Long userId,
                                                                    @RequestBody @Valid UpdateMemberRoleRequestDTO dto,
                                                                    Authentication auth) {
        return ResponseEntity.ok(walletService.updateMemberRole(auth.getName(), id, userId, dto));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id,
                                             @PathVariable Long userId,
                                             Authentication auth) {
        walletService.removeMember(auth.getName(), id, userId);
        return ResponseEntity.noContent().build();
    }
}
