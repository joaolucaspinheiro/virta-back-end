package br.com.virta.backend.dto;

/**
 * Result of adding a member. When the e-mail had no account yet, `created` is
 * true and `debugToken` carries the password-reset token for the invited user
 * (a testing helper — in production it would be e-mailed, not returned).
 */
public record AddMemberResponseDTO(
        WalletMemberResponseDTO member,
        boolean created,
        String debugToken
) {}
