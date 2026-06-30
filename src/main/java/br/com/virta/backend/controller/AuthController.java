package br.com.virta.backend.controller;

import br.com.virta.backend.dto.ForgotPasswordRequestDTO;
import br.com.virta.backend.dto.ForgotPasswordResponseDTO;
import br.com.virta.backend.dto.GoogleLoginRequestDTO;
import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.LoginResponseDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.dto.ResetPasswordRequestDTO;
import br.com.virta.backend.model.User;
import br.com.virta.backend.service.AuthService;
import br.com.virta.backend.service.JwtService;
import br.com.virta.backend.service.PasswordResetService;
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
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          PasswordResetService passwordResetService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody @Valid RegisterRequestDTO dto) {
        authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered successfully."));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        User user = authService.login(dto);
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getId(), user.getName(), user.getEmail()));
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponseDTO> google(@RequestBody @Valid GoogleLoginRequestDTO dto) {
        User user = authService.loginWithGoogle(dto.credential());
        String token = jwtService.generateToken(user);
        return ResponseEntity.ok(new LoginResponseDTO(token, user.getId(), user.getName(), user.getEmail()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO dto) {
        String debugToken = passwordResetService.requestReset(dto.email());
        return ResponseEntity.ok(new ForgotPasswordResponseDTO(
                "If this email is registered, you will receive instructions shortly.",
                debugToken));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO dto) {
        passwordResetService.reset(dto.token(), dto.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }
}
