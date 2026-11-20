package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.summary.MonthlySummaryReader;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.domain.summary.CategoryTotal;
import project.smartfinance.domain.summary.MonthlySummary;
import project.smartfinance.domain.summary.NecessityTotal;

@Repository
class JdbcMonthlySummaryReader implements MonthlySummaryReader {

    private final JdbcTemplate jdbc;

    JdbcMonthlySummaryReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public MonthlySummary forOwnerAndMonth(int ownerUserId, LocalDate month) {
        return forOwnerAndMonth(ownerUserId, month, null);
    }

    @Override
    public MonthlySummary forOwnerAndMonth(int ownerUserId, LocalDate month, Integer entityId) {
        var entries = jdbc.queryForObject("""
                SELECT COALESCE(SUM(l.valor) FILTER
                    (WHERE c.natureza = 'RECEITA'
                        AND l.status = 'EFETIVADO'), 0),
                       COALESCE(SUM(l.valor) FILTER
                    (WHERE c.natureza = 'DESPESA'
                        AND l.status = 'EFETIVADO'), 0),
                       COALESCE(SUM(l.valor) FILTER
                    (WHERE c.natureza = 'RECEITA'
                        AND l.status = 'PREVISTO'), 0),
                       COALESCE(SUM(l.valor) FILTER
                    (WHERE c.natureza = 'DESPESA'
                        AND l.status = 'PREVISTO'), 0)
                FROM lancamento_financeiro l
                JOIN categoria c
                    ON c.owner_user_id = l.owner_user_id
                    AND c.id = l.categoria_id
                WHERE l.owner_user_id = ?
                    AND l.mes_referencia = ?
                    AND l.status IN ('PREVISTO', 'EFETIVADO')
                """ + entityClause(entityId, "l.entidade_id"), (rs, row) -> new Totals(
                        rs.getBigDecimal(1), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4)),
                new Params().add(ownerUserId, month).entity(entityId).toArray());

        var installments = jdbc.queryForObject("""
                SELECT COALESCE(SUM(p.valor) FILTER
                    (WHERE f.status IN ('FECHADA', 'ATRASADA', 'PAGA')
                        AND p.status IN ('ATIVA', 'ESTORNADA')), 0),
                       COALESCE(SUM(p.valor) FILTER
                    (WHERE f.status IN ('PROJETADA', 'ABERTA')
                        AND p.status = 'ATIVA'), 0)
                FROM parcela_cartao p
                JOIN fatura_cartao f
                    ON f.owner_user_id = p.owner_user_id
                    AND f.id = p.fatura_cartao_id
                JOIN compra_cartao purchase
                    ON purchase.owner_user_id = p.owner_user_id
                    AND purchase.id = p.compra_cartao_id
                WHERE p.owner_user_id = ?
                    AND f.mes_referencia = ?
                """ + entityClause(entityId, "purchase.entidade_id"), (rs, row) -> new Totals(
                        BigDecimal.ZERO, rs.getBigDecimal(1),
                        BigDecimal.ZERO, rs.getBigDecimal(2)),
                new Params().add(ownerUserId, month).entity(entityId).toArray());

        var income = entries.income();
        var expenses = entries.expenses().add(installments.expenses());
        var plannedIncome = entries.plannedIncome();
        var plannedExpenses = entries.plannedExpenses().add(installments.plannedExpenses());
        var obligations = jdbc.queryForObject("""
                SELECT COALESCE(SUM(o.valor) FILTER (WHERE p.id IS NOT NULL), 0),
                    COALESCE(SUM(o.valor) FILTER (WHERE p.id IS NULL), 0)
                FROM conta_pagar o
                LEFT JOIN pagamento_conta_pagar p ON p.conta_pagar_id = o.id
                    AND p.owner_user_id = o.owner_user_id AND p.estornado_em IS NULL
                WHERE o.owner_user_id = ? AND o.cancelada_em IS NULL
                    AND o.data_vencimento >= ? AND o.data_vencimento < ?
                """ + entityClause(entityId, "o.entidade_id"),
                (rs, row) -> new PayableTotals(rs.getBigDecimal(1), rs.getBigDecimal(2)),
                new Params().add(ownerUserId, month, month.plusMonths(1))
                        .entity(entityId).toArray());
        expenses = expenses.add(obligations.paid());
        var pendingExpenses = obligations.pending();
        // Credits are never distributed by entity: they stay in their own indicators.
        var credits = jdbc.queryForObject("""
                SELECT COALESCE((
                    SELECT SUM(valor_original) FROM credito_estorno
                    WHERE owner_user_id = ?
                        AND criado_em >= ? AND criado_em < ?
                ), 0), COALESCE((
                    SELECT SUM(a.valor) FROM aplicacao_credito a
                    JOIN fatura_cartao f ON f.id = a.fatura_cartao_id
                        AND f.owner_user_id = a.owner_user_id
                    WHERE a.owner_user_id = ? AND f.mes_referencia = ?
                ), 0)
                """, (rs, row) -> new CreditTotals(rs.getBigDecimal(1), rs.getBigDecimal(2)),
                ownerUserId, month.atStartOfDay().atOffset(ZoneOffset.UTC),
                month.plusMonths(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                ownerUserId, month);
        List<NecessityTotal> necessityTotals = jdbc.query("""
                WITH expenses AS (
                    SELECT l.necessidade, l.valor,
                        l.status = 'EFETIVADO' AS realized, FALSE AS pending
                    FROM lancamento_financeiro l
                    JOIN categoria c ON c.id = l.categoria_id
                        AND c.owner_user_id = l.owner_user_id
                    WHERE l.owner_user_id = ? AND l.mes_referencia = ?
                        AND c.natureza = 'DESPESA'
                        AND l.status IN ('PREVISTO', 'EFETIVADO')
                """ + entityClause(entityId, "l.entidade_id") + """
                    UNION ALL
                    SELECT purchase.necessidade, installment.valor,
                        statement.status IN ('FECHADA', 'ATRASADA', 'PAGA'), FALSE
                    FROM parcela_cartao installment
                    JOIN compra_cartao purchase ON purchase.id = installment.compra_cartao_id
                        AND purchase.owner_user_id = installment.owner_user_id
                    JOIN fatura_cartao statement ON statement.id = installment.fatura_cartao_id
                        AND statement.owner_user_id = installment.owner_user_id
                    WHERE installment.owner_user_id = ? AND statement.mes_referencia = ?
                        AND ((statement.status IN ('FECHADA', 'ATRASADA', 'PAGA')
                                AND installment.status IN ('ATIVA', 'ESTORNADA'))
                            OR (statement.status IN ('PROJETADA', 'ABERTA')
                                AND installment.status = 'ATIVA'))
                """ + entityClause(entityId, "purchase.entidade_id") + """
                    UNION ALL
                    SELECT o.necessidade, o.valor, p.id IS NOT NULL, p.id IS NULL
                    FROM conta_pagar o
                    LEFT JOIN pagamento_conta_pagar p ON p.conta_pagar_id = o.id
                        AND p.owner_user_id = o.owner_user_id AND p.estornado_em IS NULL
                    WHERE o.owner_user_id = ? AND o.cancelada_em IS NULL
                        AND o.data_vencimento >= ? AND o.data_vencimento < ?
                """ + entityClause(entityId, "o.entidade_id") + """
                    )
                SELECT necessidade, COALESCE(SUM(valor) FILTER (WHERE realized), 0),
                    COALESCE(SUM(valor) FILTER (WHERE NOT realized AND NOT pending), 0),
                    COALESCE(SUM(valor) FILTER (WHERE pending), 0)
                FROM expenses
                GROUP BY necessidade
                ORDER BY necessidade NULLS LAST
                """, (rs, row) -> new NecessityTotal(
                rs.getString(1) == null ? null : Necessity.valueOf(rs.getString(1)),
                rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4)),
                new Params().add(ownerUserId, month).entity(entityId)
                        .add(ownerUserId, month).entity(entityId)
                        .add(ownerUserId, month, month.plusMonths(1)).entity(entityId).toArray());
        List<CategoryTotal> categoryTotals = jdbc.query("""
                WITH movimentos AS (
                    SELECT l.categoria_id, l.valor,
                        l.status = 'EFETIVADO' AS realizado, FALSE AS pendente
                    FROM lancamento_financeiro l
                    WHERE l.owner_user_id = ? AND l.mes_referencia = ?
                        AND l.status IN ('PREVISTO', 'EFETIVADO')
                """ + entityClause(entityId, "l.entidade_id") + """
                    UNION ALL
                    SELECT c.categoria_id, p.valor,
                        f.status IN ('FECHADA', 'ATRASADA', 'PAGA'), FALSE
                    FROM parcela_cartao p
                    JOIN compra_cartao c ON c.id = p.compra_cartao_id
                        AND c.owner_user_id = p.owner_user_id
                    JOIN fatura_cartao f ON f.id = p.fatura_cartao_id
                        AND f.owner_user_id = p.owner_user_id
                    WHERE p.owner_user_id = ? AND f.mes_referencia = ?
                        AND ((f.status IN ('FECHADA', 'ATRASADA', 'PAGA')
                                AND p.status IN ('ATIVA', 'ESTORNADA'))
                            OR (f.status IN ('PROJETADA', 'ABERTA')
                                AND p.status = 'ATIVA'))
                """ + entityClause(entityId, "c.entidade_id") + """
                    UNION ALL
                    SELECT o.categoria_id, o.valor, p.id IS NOT NULL, p.id IS NULL
                    FROM conta_pagar o
                    LEFT JOIN pagamento_conta_pagar p ON p.conta_pagar_id = o.id
                        AND p.owner_user_id = o.owner_user_id AND p.estornado_em IS NULL
                    WHERE o.owner_user_id = ? AND o.cancelada_em IS NULL
                        AND o.data_vencimento >= ? AND o.data_vencimento < ?
                """ + entityClause(entityId, "o.entidade_id") + """
                    )
                SELECT m.categoria_id, cat.nome, cat.natureza,
                    COALESCE(SUM(m.valor) FILTER (WHERE m.realizado
                        AND cat.natureza = 'RECEITA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE m.realizado
                        AND cat.natureza = 'DESPESA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE NOT m.realizado AND NOT m.pendente
                        AND cat.natureza = 'RECEITA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE NOT m.realizado AND NOT m.pendente
                        AND cat.natureza = 'DESPESA'), 0),
                    COALESCE(SUM(m.valor) FILTER (WHERE m.pendente
                        AND cat.natureza = 'DESPESA'), 0)
                FROM movimentos m
                JOIN categoria cat ON cat.id = m.categoria_id
                    AND cat.owner_user_id = ?
                GROUP BY m.categoria_id, cat.nome, cat.natureza
                ORDER BY cat.natureza DESC, cat.nome, m.categoria_id
                """, (rs, row) -> new CategoryTotal(rs.getInt(1), rs.getString(2),
                Category.Kind.valueOf(rs.getString(3)), rs.getBigDecimal(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                rs.getBigDecimal(8)),
                new Params().add(ownerUserId, month).entity(entityId)
                        .add(ownerUserId, month).entity(entityId)
                        .add(ownerUserId, month, month.plusMonths(1)).entity(entityId)
                        .add(ownerUserId).toArray());
        return new MonthlySummary(month, income, expenses, income.subtract(expenses),
                plannedIncome, plannedExpenses, pendingExpenses,
                income.add(plannedIncome).subtract(expenses).subtract(plannedExpenses)
                        .subtract(pendingExpenses),
                credits.generated(), credits.applied(), necessityTotals, categoryTotals);
    }

    private static String entityClause(Integer entityId, String column) {
        return entityId == null ? "" : " AND " + column + " = ?";
    }

    private static final class Params {

        private final List<Object> values = new ArrayList<>();

        Params add(Object... items) {
            values.addAll(List.of(items));
            return this;
        }

        Params entity(Integer entityId) {
            if (entityId != null) {
                values.add(entityId);
            }
            return this;
        }

        Object[] toArray() {
            return values.toArray();
        }
    }

    private record Totals(BigDecimal income, BigDecimal expenses,
            BigDecimal plannedIncome, BigDecimal plannedExpenses) {
    }

    private record CreditTotals(BigDecimal generated, BigDecimal applied) {
    }

    private record PayableTotals(BigDecimal paid, BigDecimal pending) {
    }
}
