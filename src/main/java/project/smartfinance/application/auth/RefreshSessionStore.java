package project.smartfinance.application.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshSessionStore {

    void create(UUID sessionId, int userId, Instant expiresAt, String tokenHash);

    Optional<UUID> findSessionId(String tokenHash);

    Optional<Session> lockSession(UUID sessionId);

    Optional<RefreshToken> findToken(String tokenHash);

    void consume(String tokenHash, Instant consumedAt);

    void insertToken(UUID sessionId, String tokenHash, Instant expiresAt);

    void revoke(UUID sessionId, Instant revokedAt);

    record Session(int userId, Instant expiresAt, Instant revokedAt) {
    }

    record RefreshToken(Instant expiresAt, Instant consumedAt) {
    }
}
