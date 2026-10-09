package br.com.virta.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 60_000;
    private static final Set<String> LIMITED_PATHS =
            Set.of("/auth/login", "/auth/forgot-password", "/auth/register");

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static class Window {
        long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        long waitSeconds = tryAcquire(key, System.currentTimeMillis());

        if (waitSeconds > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(waitSeconds));
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Muitas tentativas. Tente novamente em " + waitSeconds + " segundos.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Devolve 0 se pode passar, ou quantos segundos faltam para liberar. */
    private long tryAcquire(String key, long now) {
        Window w = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (w) {
            if (now - w.start >= WINDOW_MS) {
                w.start = now;
                w.count = 0;
            }
            if (w.count >= MAX_ATTEMPTS) {
                return (w.start + WINDOW_MS - now + 999) / 1000;
            }
            w.count++;
            return 0;
        }
    }
}