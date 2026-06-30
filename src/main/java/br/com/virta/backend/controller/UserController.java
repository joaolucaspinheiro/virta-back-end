package br.com.virta.backend.controller;

import br.com.virta.backend.dto.ChangePasswordRequestDTO;
import br.com.virta.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody @Valid ChangePasswordRequestDTO dto,
            Authentication authentication) {
        // The JWT filter sets the authenticated user's email as the principal name.
        String email = authentication.getName();
        userService.changePassword(email, dto.currentPassword(), dto.newPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully."));
    }
}
