package br.com.virta.backend.controller;

import br.com.virta.backend.dto.ForgotPasswordRequestDTO;
import br.com.virta.backend.dto.ForgotPasswordResponseDTO;
import br.com.virta.backend.dto.GoogleLoginRequestDTO;
import br.com.virta.backend.dto.LoginRequestDTO;
import br.com.virta.backend.dto.LoginResponseDTO;
import br.com.virta.backend.dto.RegisterRequestDTO;
import br.com.virta.backend.dto.ResendVerificationRequestDTO;
import br.com.virta.backend.dto.ResetPasswordRequestDTO;
import br.com.virta.backend.model.User;
import br.com.virta.backend.service.AuthService;
import br.com.virta.backend.service.EmailVerificationService;
import br.com.virta.backend.service.JwtService;
import br.com.virta.backend.dto.TotpLoginRequestDTO;
import br.com.virta.backend.dto.VerifyEmailRequestDTO;
import br.com.virta.backend.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "1. Autenticação", description = "Cadastro, confirmação de e-mail, login (senha, código TOTP e Google) e recuperação de senha. Rotas públicas.")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final PasswordResetService passwordResetService;
    private final EmailVerificationService emailVerificationService;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          PasswordResetService passwordResetService,
                          EmailVerificationService emailVerificationService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.passwordResetService = passwordResetService;
        this.emailVerificationService = emailVerificationService;
    }

    @Operation(summary = "Criar conta", description = "A conta nasce inativa: um link de confirmação é enviado por e-mail.")
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody @Valid RegisterRequestDTO dto) {
        authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "User registered. Check your e-mail to confirm the account."));
    }

    @Operation(summary = "Confirmar e-mail", description = "Recebe o token do link enviado no cadastro (vale 24 h, uso único) e ativa a conta.")
    @PostMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestBody @Valid VerifyEmailRequestDTO dto) {
        emailVerificationService.verify(dto.token());
        return ResponseEntity.ok(Map.of("message", "E-mail confirmed."));
    }

    @Operation(summary = "Reenviar confirmação", description = "Envia um novo link. A resposta é neutra: não revela se o e-mail existe.")
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(
            @RequestBody @Valid ResendVerificationRequestDTO dto) {
        emailVerificationService.resend(dto.email());
        return ResponseEntity.ok(Map.of("message",
                "If this account still needs confirmation, a new e-mail is on its way."));
    }
    @Operation(summary = "Entrar com e-mail e senha", description = "Devolve o JWT. Com a verificação em duas etapas ativa, devolve totpRequired + challengeToken (válido por 5 min).")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto) {
        User user = authService.login(dto);
        if (user.isTotpEnabled()) {
            // Password is right, but the code is still missing: no access token yet.
            return ResponseEntity.ok(LoginResponseDTO.totpChallenge(jwtService.generateTotpChallenge(user)));
        }
        return ResponseEntity.ok(authenticated(user));
    }

    @Operation(summary = "Segunda etapa do login", description = "Troca o challengeToken + código do app (ou de recuperação) pelo JWT.")
    @PostMapping("/login/totp")
    public ResponseEntity<LoginResponseDTO> loginTotp(@RequestBody @Valid TotpLoginRequestDTO dto) {
        User user = authService.loginWithTotp(dto.challengeToken(), dto.code());
        return ResponseEntity.ok(authenticated(user));
    }

    @Operation(summary = "Entrar com Google", description = "Valida o ID token do Google e devolve o JWT. Cria a conta no primeiro acesso.")
    @PostMapping("/google")
    public ResponseEntity<LoginResponseDTO> google(@RequestBody @Valid GoogleLoginRequestDTO dto) {
        User user = authService.loginWithGoogle(dto.credential());
        return ResponseEntity.ok(authenticated(user));
    }

    private LoginResponseDTO authenticated(User user) {
        return LoginResponseDTO.authenticated(
                jwtService.generateToken(user), user.getId(), user.getName(), user.getEmail());
    }

    @Operation(summary = "Esqueci a senha", description = "Envia o link de redefinição por e-mail. A resposta é sempre neutra.")
    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponseDTO> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO dto) {
        passwordResetService.requestReset(dto.email());
        return ResponseEntity.ok(new ForgotPasswordResponseDTO(
                "If this email is registered, you will receive instructions shortly.", null));
    }

    @Operation(summary = "Redefinir senha", description = "Recebe o token do e-mail e a nova senha.")
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody @Valid ResetPasswordRequestDTO dto) {
        passwordResetService.reset(dto.token(), dto.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }
}
