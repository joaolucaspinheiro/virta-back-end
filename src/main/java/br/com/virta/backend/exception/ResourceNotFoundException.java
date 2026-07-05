package br.com.virta.backend.exception;

/** Requested resource does not exist. Mapped to HTTP 404 (Not Found). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
