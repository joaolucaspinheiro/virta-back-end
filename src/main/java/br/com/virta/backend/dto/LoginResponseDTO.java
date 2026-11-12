package br.com.virta.backend.dto;

public record LoginResponseDTO(
        String token,
        Long id,
        String name,
        String email,
        boolean totpRequired,
        String challengeToken
) {
    public static LoginResponseDTO authenticated(String token, Long id, String name, String email) {
        return new LoginResponseDTO(token, id, name, email, false, null);
    }

    public static LoginResponseDTO totpChallenge(String challengeToken) {
        return new LoginResponseDTO(null, null, null, null, true, challengeToken);
    }
}
