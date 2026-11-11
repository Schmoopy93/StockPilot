CREATE TABLE auth_rate_limit_buckets (
    bucket_key CHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX auth_rate_limit_buckets_updated_at_idx
    ON auth_rate_limit_buckets (updated_at);
