package br.com.virta.backend.controller;

import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.LoginResponseDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.model.Usuario;
import br.com.virta.backend.service.AuthService;
import br.com.virta.backend.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody @Valid RegisterRequestDTO dto) {
        authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("mensagem", "Usuário cadastrado com sucesso."));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        Usuario usuario = authService.login(dto);
        String token = jwtService.generateToken(usuario);
        return ResponseEntity.ok(new LoginResponseDTO(token, usuario.getId(), usuario.getNome(), usuario.getEmail()));
    }
}
