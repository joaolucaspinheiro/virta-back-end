package br.com.virta.backend.exception;

public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException() {
        super("Invalid or expired Google token.");
    }
}
