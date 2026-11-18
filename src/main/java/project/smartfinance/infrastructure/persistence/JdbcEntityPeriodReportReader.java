package project.smartfinance.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.summary.EntityPeriodReportReader;
import project.smartfinance.domain.summary.EntityPeriodReport;
import project.smartfinance.domain.summary.EntityPeriodTotal;

@Repository
class JdbcEntityPeriodReportReader implements EntityPeriodReportReader {

    private final JdbcTemplate jdbc;

    JdbcEntityPeriodReportReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EntityPeriodReport report(int ownerUserId, LocalDate from, LocalDate to) {
        List<EntityPeriodTotal> entities = jdbc.query("""
                WITH movimentos AS (
                    SELECT l.entidade_id, c.natureza, l.valor,
                        l.status = 'EFETIVADO' AS realizado
                    FROM lancamento_financeiro l
                    JOIN categoria c ON c.id = l.categoria_id
                        AND c.owner_user_id = l.owner_user_id
                    WHERE l.owner_user_id = ?
                        AND l.mes_referencia BETWEEN ? AND ?
                        AND l.status IN ('PREVISTO', 'EFETIVADO')
                    UNION ALL
                    SELECT purchase.entidade_id, cat.natureza, p.valor,
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
                SELECT m.entidade_id, COALESCE(e.nome, 'Sem entidade'),
                    COALESCE(SUM(m.valor) FILTER (WHERE m.realizado
                        AND m.natureza = 'RECEITA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE m.realizado
                        AND m.natureza = 'DESPESA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE NOT m.realizado
                        AND m.natureza = 'RECEITA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE NOT m.realizado
                        AND m.natureza = 'DESPESA'), 0)
                FROM movimentos m
                LEFT JOIN entidade e ON e.id = m.entidade_id
                    AND e.owner_user_id = ?
                GROUP BY m.entidade_id, e.nome
                ORDER BY COALESCE(e.nome, 'Sem entidade'), m.entidade_id
                """, (rs, row) -> new EntityPeriodTotal(
                rs.getObject(1, Integer.class), rs.getString(2),
                rs.getBigDecimal(3), rs.getBigDecimal(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6)),
                ownerUserId, from, to, ownerUserId, from, to, ownerUserId);
        return new EntityPeriodReport(from, to, entities);
    }
}
