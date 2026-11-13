package br.com.virta.backend.controller;

import br.com.virta.backend.dto.AddMemberRequestDTO;
import br.com.virta.backend.dto.AddMemberResponseDTO;
import br.com.virta.backend.dto.UpdateMemberRoleRequestDTO;
import br.com.virta.backend.dto.WalletMemberResponseDTO;
import br.com.virta.backend.dto.WalletRequestDTO;
import br.com.virta.backend.dto.WalletResponseDTO;
import br.com.virta.backend.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "4. Carteiras", description = "Carteiras pessoais e compartilhadas, membros e papéis (OWNER, EDITOR, VIEWER).")
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @Operation(summary = "Minhas carteiras", description = "Carteiras das quais o usuário é membro, com o papel dele em cada uma.")
    @GetMapping
    public ResponseEntity<List<WalletResponseDTO>> list(Authentication auth) {
        return ResponseEntity.ok(walletService.list(auth.getName()));
    }

    @Operation(summary = "Criar carteira", description = "O criador vira OWNER.")
    @PostMapping
    public ResponseEntity<WalletResponseDTO> create(@RequestBody @Valid WalletRequestDTO dto,
                                                    Authentication auth) {
        WalletResponseDTO created = walletService.create(auth.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Operation(summary = "Detalhar carteira")
    @GetMapping("/{id}")
    public ResponseEntity<WalletResponseDTO> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(walletService.get(auth.getName(), id));
    }

    @Operation(summary = "Renomear carteira", description = "Somente o OWNER.")
    @PutMapping("/{id}")
    public ResponseEntity<WalletResponseDTO> update(@PathVariable Long id,
                                                    @RequestBody @Valid WalletRequestDTO dto,
                                                    Authentication auth) {
        return ResponseEntity.ok(walletService.update(auth.getName(), id, dto));
    }

    @Operation(summary = "Excluir carteira", description = "Somente o OWNER, e não é possível excluir a última carteira de que é dono.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        walletService.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Membros da carteira")
    @GetMapping("/{id}/members")
    public ResponseEntity<List<WalletMemberResponseDTO>> listMembers(@PathVariable Long id,
                                                                     Authentication auth) {
        return ResponseEntity.ok(walletService.listMembers(auth.getName(), id));
    }

    @Operation(summary = "Convidar membro", description = "Somente o OWNER. Se o e-mail não tem conta, cria um convite e envia por e-mail.")
    @PostMapping("/{id}/members")
    public ResponseEntity<AddMemberResponseDTO> addMember(@PathVariable Long id,
                                                          @RequestBody @Valid AddMemberRequestDTO dto,
                                                          Authentication auth) {
        AddMemberResponseDTO result = walletService.addMember(auth.getName(), id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(summary = "Alterar papel de um membro", description = "Somente o OWNER.")
    @PatchMapping("/{id}/members/{userId}")
    public ResponseEntity<WalletMemberResponseDTO> updateMemberRole(@PathVariable Long id,
                                                                    @PathVariable Long userId,
                                                                    @RequestBody @Valid UpdateMemberRoleRequestDTO dto,
                                                                    Authentication auth) {
        return ResponseEntity.ok(walletService.updateMemberRole(auth.getName(), id, userId, dto));
    }

    @Operation(summary = "Remover membro", description = "Somente o OWNER.")
    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable Long id,
                                             @PathVariable Long userId,
                                             Authentication auth) {
        walletService.removeMember(auth.getName(), id, userId);
        return ResponseEntity.noContent().build();
    }
}
