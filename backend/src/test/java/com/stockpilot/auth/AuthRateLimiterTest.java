package com.stockpilot.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class AuthRateLimiterTest {

    @Mock
    private JdbcTemplate jdbc;

    private AuthRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AuthRateLimiter(jdbc, "test-secret-with-more-than-thirty-two-bytes");
    }

    @Test
    void allowedRequestReturnsLimitAndRemainingCountWithoutStoringClientAddress() {
        when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of(3));

        AuthRateLimiter.Result result = limiter.tryAcquire("login", "203.0.113.7", 10, 60);

        ArgumentCaptor<String> bucketKey = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(any(String.class), any(RowMapper.class), bucketKey.capture(), eq(60), eq(10));
        assertThat(bucketKey.getValue()).hasSize(64).doesNotContain("203.0.113.7");
        assertThat(result).isEqualTo(new AuthRateLimiter.Result(true, 0, 10, 7));
    }

    @Test
    void deniedRequestReturnsDatabaseWindowRetryTime() {
        when(jdbc.query(any(String.class), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());
        when(jdbc.queryForObject(any(String.class), eq(Long.class), eq(3600), any(String.class)))
                .thenReturn(143L);

        AuthRateLimiter.Result result = limiter.tryAcquire("forgot-password", "203.0.113.7", 5, 3600);

        assertThat(result).isEqualTo(new AuthRateLimiter.Result(false, 143, 5, 0));
    }

    @Test
    void rejectsShortHmacSecrets() {
        assertThatThrownBy(() -> new AuthRateLimiter(jdbc, "too-short"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
