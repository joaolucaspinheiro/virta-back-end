package br.com.virta.backend.exception;

/** Mapped to HTTP 429 by the GlobalExceptionHandler. */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}