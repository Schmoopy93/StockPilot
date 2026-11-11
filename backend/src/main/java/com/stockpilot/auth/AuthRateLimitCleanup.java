package com.stockpilot.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("!demo")
public class AuthRateLimitCleanup {

    private final JdbcTemplate jdbc;

    public AuthRateLimitCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${app.auth.rate-limit-cleanup-delay:3600000}")
    public void removeInactiveBuckets() {
        jdbc.update("""
                DELETE FROM auth_rate_limit_buckets
                WHERE updated_at < statement_timestamp() - INTERVAL '24 hours'
                """);
    }
}
