package com.stockpilot.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@Profile("!demo")
public class AuthRateLimiter {

    private static final String UPSERT_SQL = """
            INSERT INTO auth_rate_limit_buckets (bucket_key, window_started_at, request_count, updated_at)
            VALUES (?, date_bin(make_interval(secs => ?), statement_timestamp(), TIMESTAMPTZ 'epoch'),
                    1, statement_timestamp())
            ON CONFLICT (bucket_key) DO UPDATE
            SET window_started_at = EXCLUDED.window_started_at,
                request_count = CASE
                    WHEN auth_rate_limit_buckets.window_started_at = EXCLUDED.window_started_at
                    THEN auth_rate_limit_buckets.request_count + 1
                    ELSE 1
                END,
                updated_at = statement_timestamp()
            WHERE auth_rate_limit_buckets.window_started_at <> EXCLUDED.window_started_at
               OR auth_rate_limit_buckets.request_count < ?
            RETURNING request_count
            """;

    private static final String RETRY_AFTER_SQL = """
            SELECT GREATEST(
                1,
                CEIL(EXTRACT(EPOCH FROM (
                    window_started_at + make_interval(secs => ?) - statement_timestamp()
                )))
            )::BIGINT
            FROM auth_rate_limit_buckets
            WHERE bucket_key = ?
            """;

    private final JdbcTemplate jdbc;
    private final byte[] hmacSecret;

    public AuthRateLimiter(JdbcTemplate jdbc,
                           @Value("${app.auth.rate-limit-secret}") String hmacSecret) {
        this.jdbc = jdbc;
        this.hmacSecret = hmacSecret.getBytes(StandardCharsets.UTF_8);
        if (this.hmacSecret.length < 32) {
            throw new IllegalArgumentException("Authentication rate-limit secret must be at least 32 bytes");
        }
    }

    public Result tryAcquire(String endpoint, String clientAddress, int limit, int windowSeconds) {
        if (endpoint == null || endpoint.isBlank() || clientAddress == null || clientAddress.isBlank()) {
            throw new IllegalArgumentException("Endpoint and client address are required for rate limiting");
        }
        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Rate limit and window must be positive");
        }

        String bucketKey = bucketKey(endpoint, clientAddress);
        var counts = jdbc.query(UPSERT_SQL,
                (resultSet, rowNumber) -> resultSet.getInt("request_count"),
                bucketKey, windowSeconds, limit);
        if (!counts.isEmpty()) {
            int remaining = Math.max(0, limit - counts.getFirst());
            return new Result(true, 0, limit, remaining);
        }

        Long retryAfter = jdbc.queryForObject(RETRY_AFTER_SQL, Long.class, windowSeconds, bucketKey);
        if (retryAfter == null) {
            throw new IllegalStateException("Rate-limit bucket disappeared while calculating retry time");
        }
        return new Result(false, retryAfter, limit, 0);
    }

    private String bucketKey(String endpoint, String clientAddress) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            byte[] digest = mac.doFinal((endpoint + "\n" + clientAddress).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HmacSHA256 is required by the Java runtime", exception);
        }
    }

    public record Result(boolean allowed, long retryAfterSeconds, int limit, int remaining) {}
}
