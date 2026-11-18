package project.smartfinance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.summary.MonthlyPointReader;
import project.smartfinance.domain.summary.MonthlyPoint;

@Repository
class JdbcMonthlyPointReader implements MonthlyPointReader {

    private final JdbcTemplate jdbc;

    JdbcMonthlyPointReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MonthlyPoint> read(int ownerUserId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                WITH movimentos AS (
                    SELECT l.mes_referencia AS mes, c.natureza, l.valor,
                        l.status = 'EFETIVADO' AS realizado
                    FROM lancamento_financeiro l
                    JOIN categoria c ON c.id = l.categoria_id
                        AND c.owner_user_id = l.owner_user_id
                    WHERE l.owner_user_id = ?
                        AND l.mes_referencia BETWEEN ? AND ?
                        AND l.status IN ('PREVISTO', 'EFETIVADO')
                    UNION ALL
                    SELECT f.mes_referencia, cat.natureza, p.valor,
                        f.status IN ('FECHADA', 'ATRASADA', 'PAGA')
                    FROM parcela_cartao p
                    JOIN compra_cartao purchase ON purchase.id = p.compra_cartao_id
                        AND purchase.owner_user_id = p.owner_user_id
                    JOIN categoria cat ON cat.id = purchase.categoria_id
                        AND cat.owner_user_id = purchase.owner_user_id
                    JOIN fatura_cartao f ON f.id = p.fatura_cartao_id
                        AND f.owner_user_id = p.owner_user_id
                    WHERE p.owner_user_id = ?
                        AND f.mes_referencia BETWEEN ? AND ?
                        AND ((f.status IN ('FECHADA', 'ATRASADA', 'PAGA')
                                AND p.status IN ('ATIVA', 'ESTORNADA'))
                            OR (f.status IN ('PROJETADA', 'ABERTA')
                                AND p.status = 'ATIVA'))
                )
                SELECT mes,
                    COALESCE(SUM(valor) FILTER (WHERE realizado
                        AND natureza = 'RECEITA'), 0),
                    COALESCE(SUM(valor) FILTER (WHERE realizado
                        AND natureza = 'DESPESA'), 0),
                    COALESCE(SUM(valor) FILTER (WHERE NOT realizado
                        AND natureza = 'RECEITA'), 0),
                    COALESCE(SUM(valor) FILTER (WHERE NOT realizado
                        AND natureza = 'DESPESA'), 0)
                FROM movimentos
                GROUP BY mes
                ORDER BY mes
                """, (rs, row) -> MonthlyPoint.of(rs.getDate(1).toLocalDate(),
                rs.getBigDecimal(2), rs.getBigDecimal(3),
                rs.getBigDecimal(4), rs.getBigDecimal(5)),
                ownerUserId, from, to, ownerUserId, from, to);
    }
}
