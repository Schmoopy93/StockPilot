package com.stockpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthRateLimitFilterTest {

    @Mock
    private AuthRateLimiter rateLimiter;

    @Mock
    private FilterChain filterChain;

    @Mock
    private AuthClientAddressResolver addressResolver;

    @Test
    void rateLimitedLoginReturns429AndRetryAfter() throws Exception {
        when(rateLimiter.tryAcquire("login", "127.0.0.1", 10, 60))
                .thenReturn(new AuthRateLimiter.Result(false, 37, 10, 0));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(rateLimiter, addressResolver, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("127.0.0.1");
        when(addressResolver.resolve(request)).thenReturn("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("37");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(response.getContentAsString()).contains("Previše zahteva");
        org.mockito.Mockito.verifyNoInteractions(filterChain);
    }

    @Test
    void unlistedRequestsAreNotRateLimited() throws Exception {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(rateLimiter, addressResolver, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        org.mockito.Mockito.verifyNoInteractions(rateLimiter);
    }

    @Test
    void resendVerificationUsesAnIndependentHourlyLimit() throws Exception {
        when(rateLimiter.tryAcquire("resend-verification", "127.0.0.1", 5, 3600))
                .thenReturn(new AuthRateLimiter.Result(true, 0, 5, 4));
        AuthRateLimitFilter filter = new AuthRateLimitFilter(rateLimiter, addressResolver, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/auth/resend-verification");
        request.setRemoteAddr("127.0.0.1");
        when(addressResolver.resolve(request)).thenReturn("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(rateLimiter).tryAcquire("resend-verification", "127.0.0.1", 5, 3600);
        verify(filterChain).doFilter(request, response);
    }
}
