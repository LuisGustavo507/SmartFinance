package project.smartfinance.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Security-relevant events written to a dedicated logger so they can be
 * filtered and retained independently from ordinary application logs.
 * Never log credentials, tokens or personal data here.
 */
@Component
public class SecurityAudit {

    private static final Logger log = LoggerFactory.getLogger("project.smartfinance.audit");

    public void userRegistered(int userId) {
        log.info("event=user_registered userId={}", userId);
    }

    public void loginSucceeded(int userId) {
        log.info("event=login_succeeded userId={}", userId);
    }

    public void loginFailed() {
        log.warn("event=login_failed reason=invalid_credentials");
    }

    public void refreshRejected(String reason) {
        log.warn("event=refresh_rejected reason={}", reason);
    }

    public void refreshRejected(String reason, String sessionId) {
        log.warn("event=refresh_rejected reason={} sessionId={}", reason, sessionId);
    }

    public void sessionRevoked(String sessionId) {
        log.info("event=session_revoked sessionId={}", sessionId);
    }
}
