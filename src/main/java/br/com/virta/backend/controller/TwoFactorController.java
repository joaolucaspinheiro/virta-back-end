package br.com.virta.backend.controller;

import br.com.virta.backend.dto.TotpCodeRequestDTO;
import br.com.virta.backend.dto.TotpEnableResponseDTO;
import br.com.virta.backend.dto.TotpSetupResponseDTO;
import br.com.virta.backend.service.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "3. Verificação em duas etapas", description = "Ativar e desativar o TOTP (app autenticador) com códigos de recuperação.")
@RestController
@RequestMapping("/api/v1/users/me/totp")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    public TwoFactorController(TwoFactorService twoFactorService) {
        this.twoFactorService = twoFactorService;
    }

    @Operation(summary = "Iniciar ativação do TOTP", description = "Gera o segredo e o conteúdo do QR code. Ainda não ativa.")
    @PostMapping("/setup")
    public ResponseEntity<TotpSetupResponseDTO> setup(Authentication auth) {
        return ResponseEntity.ok(twoFactorService.setup(auth.getName()));
    }

    @Operation(summary = "Confirmar e ativar o TOTP", description = "Recebe um código do app e devolve 8 códigos de recuperação (mostrados uma única vez).")
    @PostMapping("/enable")
    public ResponseEntity<TotpEnableResponseDTO> enable(@RequestBody @Valid TotpCodeRequestDTO dto,
                                                        Authentication auth) {
        List<String> recoveryCodes = twoFactorService.enable(auth.getName(), dto.code());
        return ResponseEntity.ok(new TotpEnableResponseDTO("Two-factor authentication enabled.", recoveryCodes));
    }

    @Operation(summary = "Desativar o TOTP", description = "Exige um código do app ou de recuperação.")
    @PostMapping("/disable")
    public ResponseEntity<Map<String, String>> disable(@RequestBody @Valid TotpCodeRequestDTO dto,
                                                       Authentication auth) {
        twoFactorService.disable(auth.getName(), dto.code());
        return ResponseEntity.ok(Map.of("message", "Two-factor authentication disabled."));
    }
}