package br.com.virta.backend.exception;

/** State conflict (e.g. duplicate member). Mapped to HTTP 409 (Conflict). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
