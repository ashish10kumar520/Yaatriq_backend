CREATE TABLE oauth2_authorization_requests (
    state VARCHAR(255) PRIMARY KEY,
    request_data JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_oauth2_authorization_requests_expires_at
    ON oauth2_authorization_requests(expires_at);
