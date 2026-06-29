package br.com.virta.backend.exception;

public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException() {
        super("Token do Google inválido ou expirado.");
    }
}
