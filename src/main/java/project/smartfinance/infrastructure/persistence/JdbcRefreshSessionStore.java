package project.smartfinance.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.auth.RefreshSessionStore;

@Repository
class JdbcRefreshSessionStore implements RefreshSessionStore {

    private final JdbcTemplate jdbc;

    JdbcRefreshSessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID sessionId, int userId, Instant expiresAt, String tokenHash) {
        jdbc.update("INSERT INTO auth_session (id, user_id, expires_at) VALUES (?, ?, ?)",
                sessionId, userId, Timestamp.from(expiresAt));
        insertToken(sessionId, tokenHash, expiresAt);
    }

    @Override
    public Optional<UUID> findSessionId(String tokenHash) {
        return jdbc.query("""
                SELECT session_id FROM auth_refresh_token WHERE token_hash = ?
                """, rs -> rs.next() ? Optional.of((UUID) rs.getObject(1)) : Optional.empty(),
                tokenHash);
    }

    @Override
    public Optional<Session> lockSession(UUID sessionId) {
        return jdbc.query("""
                SELECT user_id, expires_at, revoked_at
                FROM auth_session WHERE id = ? FOR UPDATE
                """, rs -> rs.next()
                ? Optional.of(new Session(rs.getInt(1),
                        rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()))
                : Optional.empty(), sessionId);
    }

    @Override
    public Optional<RefreshToken> findToken(String tokenHash) {
        return jdbc.query("""
                SELECT expires_at, consumed_at
                FROM auth_refresh_token WHERE token_hash = ?
                """, rs -> rs.next()
                ? Optional.of(new RefreshToken(rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()))
                : Optional.empty(), tokenHash);
    }

    @Override
    public void consume(String tokenHash, Instant consumedAt) {
        jdbc.update("UPDATE auth_refresh_token SET consumed_at = ? WHERE token_hash = ?",
                Timestamp.from(consumedAt), tokenHash);
    }

    @Override
    public void insertToken(UUID sessionId, String tokenHash, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO auth_refresh_token (token_hash, session_id, expires_at)
                VALUES (?, ?, ?)
                """, tokenHash, sessionId, Timestamp.from(expiresAt));
    }

    @Override
    public void revoke(UUID sessionId, Instant revokedAt) {
        jdbc.update("UPDATE auth_session SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt), sessionId);
    }
}
