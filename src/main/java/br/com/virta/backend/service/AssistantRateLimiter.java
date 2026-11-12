package br.com.virta.backend.service;

import br.com.virta.backend.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-user limit for the assistant, so one person can't burn the free Gemini quota. */
@Component
public class AssistantRateLimiter {

    private static final int MAX_MESSAGES = 15;
    private static final long WINDOW_MS = 10 * 60_000; // 10 minutes

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static class Window {
        long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }

    public void check(String email) {
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(email, k -> new Window(now));
        synchronized (w) {
            if (now - w.start >= WINDOW_MS) {
                w.start = now;
                w.count = 0;
            }
            if (w.count >= MAX_MESSAGES) {
                long minutes = (w.start + WINDOW_MS - now + 59_999) / 60_000;
                throw new TooManyRequestsException(
                        "Limite do assistente atingido. Tente de novo em " + minutes + " min.");
            }
            w.count++;
        }
    }
}