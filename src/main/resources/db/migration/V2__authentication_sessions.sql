CREATE TABLE auth_session (
    id UUID PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES usuario (id),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ
);

CREATE TABLE auth_refresh_token (
    token_hash CHAR(64) PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_session (id),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_auth_session_user_id ON auth_session (user_id);
CREATE INDEX idx_auth_refresh_token_session_id ON auth_refresh_token (session_id);
