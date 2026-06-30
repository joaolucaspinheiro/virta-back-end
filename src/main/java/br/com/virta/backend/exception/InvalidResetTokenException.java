package br.com.virta.backend.exception;

public class InvalidResetTokenException extends RuntimeException {
    public InvalidResetTokenException() {
        super("Invalid, expired or already used reset token.");
    }
}
