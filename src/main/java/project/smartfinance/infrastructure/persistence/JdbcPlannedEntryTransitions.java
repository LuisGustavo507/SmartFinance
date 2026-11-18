package project.smartfinance.infrastructure.persistence;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.entry.PlannedEntryTransitions;

@Repository
class JdbcPlannedEntryTransitions implements PlannedEntryTransitions {

    private final JdbcTemplate jdbc;

    JdbcPlannedEntryTransitions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int realizeDue(LocalDate today, OffsetDateTime processedAt) {
        return jdbc.update("""
                UPDATE lancamento_financeiro
                SET status = 'EFETIVADO',
                    data_ocorrencia = data_prevista,
                    efetivado_em = ?
                WHERE status = 'PREVISTO'
                    AND data_prevista <= ?
                """, Timestamp.from(processedAt.toInstant()), Date.valueOf(today));
    }

    @Override
    public boolean cancel(int id, int ownerUserId) {
        return jdbc.update("""
                UPDATE lancamento_financeiro SET status = 'CANCELADO'
                WHERE id = ? AND owner_user_id = ? AND status = 'PREVISTO'
                """, id, ownerUserId) == 1;
    }

    @Override
    public boolean existsForOwner(int id, int ownerUserId) {
        return Boolean.TRUE.equals(jdbc.query("""
                SELECT EXISTS (
                    SELECT 1 FROM lancamento_financeiro
                    WHERE id = ? AND owner_user_id = ?
                )
                """, rs -> rs.next() && rs.getBoolean(1), id, ownerUserId));
    }
}
