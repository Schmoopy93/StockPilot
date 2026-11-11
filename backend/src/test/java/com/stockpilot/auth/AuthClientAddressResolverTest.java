package com.stockpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthClientAddressResolverTest {

    @Test
    void ignoresForwardedAddressFromUntrustedPeer() {
        AuthClientAddressResolver resolver = new AuthClientAddressResolver("");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.8");
        request.addHeader("X-Forwarded-For", "203.0.113.15");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.8");
    }

    @Test
    void resolvesClientThroughConfiguredTrustedProxyChain() {
        AuthClientAddressResolver resolver = new AuthClientAddressResolver("10.0.0.0/8, 192.0.2.10");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.3");
        request.addHeader("X-Forwarded-For", "203.0.113.15, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.15");
    }

    @Test
    void fallsBackToTrustedPeerWhenForwardedChainIsMalformed() {
        AuthClientAddressResolver resolver = new AuthClientAddressResolver("10.0.0.0/8");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.3");
        request.addHeader("X-Forwarded-For", "untrusted-value");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.3");
    }

    @Test
    void rejectsMalformedTrustedProxyConfiguration() {
        assertThatThrownBy(() -> new AuthClientAddressResolver("proxy.example.test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid trusted proxy range");
    }
}
