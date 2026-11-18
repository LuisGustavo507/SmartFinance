package project.smartfinance.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.user.BetaInviteStore;

@Repository
class JdbcBetaInviteStore implements BetaInviteStore {

    private final JdbcTemplate jdbc;

    JdbcBetaInviteStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isPending(String email) {
        Boolean pending = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM convite_beta WHERE email = ? AND usado_em IS NULL
                )
                """, Boolean.class, email);
        return Boolean.TRUE.equals(pending);
    }

    @Override
    public int consume(String email, int userId, Instant usedAt) {
        return jdbc.update("""
                UPDATE convite_beta
                SET usado_em = ?, usado_por = ?
                WHERE email = ? AND usado_em IS NULL
                """, Timestamp.from(usedAt), userId, email);
    }
}
