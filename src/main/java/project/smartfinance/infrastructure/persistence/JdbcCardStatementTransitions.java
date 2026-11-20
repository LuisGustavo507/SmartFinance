package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.card.CardStatementTransitions;

@Repository
class JdbcCardStatementTransitions implements CardStatementTransitions {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcCardStatementTransitions(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public int regularize(LocalDate today, OffsetDateTime processedAt) {
        int opened = jdbc.update("""
                UPDATE fatura_cartao SET status = 'ABERTA'
                WHERE status = 'PROJETADA'
                    AND (data_fechamento - INTERVAL '1 month')::date <= ?
                """, Date.valueOf(today));

        // Chronological order matters: a credit left over from one closing must
        // be available for the next closing, even after a long outage.
        List<Closing> closings = jdbc.query("""
                SELECT id, owner_user_id, cartao_id, data_fechamento
                FROM fatura_cartao
                WHERE status = 'ABERTA' AND data_fechamento <= ?
                ORDER BY data_fechamento, id
                FOR UPDATE
                """, (rs, row) -> new Closing(rs.getInt(1), rs.getInt(2),
                rs.getInt(3), rs.getDate(4).toLocalDate()), Date.valueOf(today));
        for (Closing closing : closings) {
            close(closing, processedAt);
        }

        int overdue = jdbc.update("""
                UPDATE fatura_cartao SET status = 'ATRASADA'
                WHERE status = 'FECHADA' AND data_vencimento < ?
                """, Date.valueOf(today));
        return opened + closings.size() + overdue;
    }

    private void close(Closing closing, OffsetDateTime processedAt) {
        BigDecimal gross = jdbc.queryForObject("""
                SELECT COALESCE(SUM(valor), 0) FROM parcela_cartao
                WHERE fatura_cartao_id = ? AND status = 'ATIVA'
                """, BigDecimal.class, closing.id());
        BigDecimal remaining = gross;
        List<Credit> credits = jdbc.query("""
                SELECT c.id, c.valor_original - COALESCE(SUM(a.valor), 0)
                FROM credito_estorno c
                JOIN compra_cartao p ON p.id = c.compra_cartao_id
                    AND p.owner_user_id = c.owner_user_id
                LEFT JOIN aplicacao_credito a ON a.credito_estorno_id = c.id
                WHERE c.owner_user_id = ? AND p.cartao_id = ?
                    AND c.criado_em < ?
                GROUP BY c.id
                HAVING c.valor_original > COALESCE(SUM(a.valor), 0)
                ORDER BY MIN(c.criado_em), c.id
                """, (rs, row) -> new Credit(rs.getInt(1), rs.getBigDecimal(2)),
                closing.ownerId(), closing.cardId(),
                closing.closingDate().atStartOfDay(clock.getZone()).toOffsetDateTime());
        for (Credit credit : credits) {
            if (remaining.signum() == 0) {
                break;
            }
            BigDecimal used = remaining.min(credit.available());
            jdbc.update("""
                    INSERT INTO aplicacao_credito
                        (owner_user_id, credito_estorno_id, fatura_cartao_id, valor, criada_em)
                    VALUES (?, ?, ?, ?, ?)
                    """, closing.ownerId(), credit.id(), closing.id(), used, processedAt);
            remaining = remaining.subtract(used);
        }
        jdbc.update("""
                UPDATE fatura_cartao
                SET status = ?, valor_fechado = ?
                WHERE id = ? AND status = 'ABERTA'
                """, remaining.signum() == 0 ? "PAGA" : "FECHADA", remaining, closing.id());
    }

    private record Closing(int id, int ownerId, int cardId, LocalDate closingDate) {
    }

    private record Credit(int id, BigDecimal available) {
    }
}
