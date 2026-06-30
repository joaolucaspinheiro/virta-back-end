package br.com.virta.backend.exception;

/** Business rule violation. Mapped to HTTP 422 (Unprocessable Entity). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
