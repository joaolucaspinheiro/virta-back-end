package br.com.virta.backend.exception;

/** Right password, but the account was never confirmed by e-mail. Mapped to HTTP 403. */
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException() {
        super("Confirm your e-mail to sign in.");
    }
}
