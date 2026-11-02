package br.com.virta.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the brute-force protection on the sensitive auth routes. */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void blocksTheSixthLoginAttemptWithinAMinute() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertThat(send("POST", "/auth/login", "10.0.0.1").getStatus())
                    .as("attempt %d", i).isEqualTo(200);
        }

        MockHttpServletResponse blocked = send("POST", "/auth/login", "10.0.0.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("Muitas tentativas");
    }

    @Test
    void countsEachIpSeparately() throws Exception {
        for (int i = 0; i < 6; i++) {
            send("POST", "/auth/login", "10.0.0.1");
        }

        assertThat(send("POST", "/auth/login", "10.0.0.2").getStatus()).isEqualTo(200);
    }

    @Test
    void doesNotLimitOtherRoutesOrMethods() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(send("GET", "/api/v1/wallets", "10.0.0.1").getStatus()).isEqualTo(200);
            assertThat(send("GET", "/auth/login", "10.0.0.1").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse send(String method, String uri, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
