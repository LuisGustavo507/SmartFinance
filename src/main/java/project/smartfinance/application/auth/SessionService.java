package project.smartfinance.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.user.UserAccountStore;
import project.smartfinance.application.user.UserCredentials;
import project.smartfinance.infrastructure.audit.SecurityAudit;

@Service
public class SessionService {

    private final UserAccountStore users;
    private final RefreshSessionStore sessions;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokens;
    private final SecurityAudit audit;
    private final Clock clock;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public SessionService(UserAccountStore users, RefreshSessionStore sessions,
            PasswordEncoder passwordEncoder, AccessTokenIssuer accessTokens, SecurityAudit audit,
            Clock clock, @Value("${smartfinance.security.refresh-ttl:P7D}") Duration refreshTtl) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.audit = audit;
        this.clock = clock;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public Tokens login(String email, String password) {
        UserCredentials user = users.findByEmail(email.toLowerCase(Locale.ROOT)).orElse(null);
        if (user == null || !user.active()
                || !passwordEncoder.matches(password, user.passwordHash())) {
            audit.loginFailed();
            throw new InvalidSessionException();
        }
        audit.loginSucceeded(user.id());

        String refresh = newRefreshToken();
        Instant expiresAt = clock.instant().plus(refreshTtl);
        sessions.create(UUID.randomUUID(), user.id(), expiresAt, hash(refresh));
        return new Tokens(accessTokens.issue(user), refresh, expiresAt);
    }

    @Transactional(noRollbackFor = InvalidSessionException.class)
    public Tokens refresh(String previousRefresh) {
        if (previousRefresh == null || previousRefresh.isBlank()) {
            audit.refreshRejected("missing_token");
            throw new InvalidSessionException();
        }
        String previousHash = hash(previousRefresh);
        UUID sessionId = sessions.findSessionId(previousHash).orElse(null);
        if (sessionId == null) {
            audit.refreshRejected("unknown_token");
            throw new InvalidSessionException();
        }

        // The family lock serializes requests racing to rotate the same token.
        RefreshSessionStore.Session session = sessions.lockSession(sessionId).orElse(null);
        if (session == null) {
            audit.refreshRejected("unknown_session", sessionId.toString());
            throw new InvalidSessionException();
        }
        if (session.revokedAt() != null) {
            audit.refreshRejected("revoked_session", sessionId.toString());
            throw new InvalidSessionException();
        }
        RefreshSessionStore.RefreshToken token = sessions.findToken(previousHash).orElse(null);
        if (token == null) {
            audit.refreshRejected("unknown_token", sessionId.toString());
            throw new InvalidSessionException();
        }
        if (token.consumedAt() != null) {
            audit.refreshRejected("reused_token", sessionId.toString());
            sessions.revoke(sessionId, clock.instant());
            throw new InvalidSessionException();
        }
        Instant now = clock.instant();
        if (!now.isBefore(session.expiresAt()) || !now.isBefore(token.expiresAt())) {
            audit.refreshRejected("expired_session", sessionId.toString());
            sessions.revoke(sessionId, now);
            throw new InvalidSessionException();
        }
        UserCredentials user = users.findById(session.userId())
                .filter(UserCredentials::active)
                .orElse(null);
        if (user == null) {
            audit.refreshRejected("inactive_user", sessionId.toString());
            sessions.revoke(sessionId, now);
            throw new InvalidSessionException();
        }

        String refresh = newRefreshToken();
        sessions.consume(previousHash, now);
        sessions.insertToken(sessionId, hash(refresh), session.expiresAt());
        return new Tokens(accessTokens.issue(user), refresh, session.expiresAt());
    }

    @Transactional
    public void logout(String refresh) {
        if (refresh == null || refresh.isBlank()) {
            return;
        }
        sessions.findSessionId(hash(refresh)).ifPresent(sessionId -> {
            if (sessions.lockSession(sessionId).isPresent()) {
                sessions.revoke(sessionId, clock.instant());
                audit.sessionRevoked(sessionId.toString());
            }
        });
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Tokens(String access, String refresh, Instant refreshExpiresAt) {
    }
}
