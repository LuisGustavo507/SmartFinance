ALTER TABLE auth_refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64);
