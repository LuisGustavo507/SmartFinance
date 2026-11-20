package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.payable.PayableStore;
import project.smartfinance.application.payable.Payables;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.domain.payable.Payable;
import project.smartfinance.domain.payable.PayablePayment;

@Repository
class JdbcPayableStore implements PayableStore {
    private static final String SELECT_PAYABLES = """
            SELECT o.id, o.owner_user_id, o.categoria_id, c.nome,
                o.entidade_id, e.nome, o.descricao, o.necessidade,
                o.valor, o.data_vencimento, o.cancelada_em, o.criada_em,
                p.id IS NOT NULL AS paid
            FROM conta_pagar o
            JOIN categoria c ON c.id = o.categoria_id AND c.owner_user_id = o.owner_user_id
            LEFT JOIN entidade e ON e.id = o.entidade_id AND e.owner_user_id = o.owner_user_id
            LEFT JOIN pagamento_conta_pagar p ON p.conta_pagar_id = o.id
                AND p.owner_user_id = o.owner_user_id AND p.estornado_em IS NULL
            """;

    private final JdbcTemplate jdbc;

    JdbcPayableStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Payable create(int ownerId, int categoryId, Integer entityId, String description,
            String necessity, BigDecimal amount, LocalDate dueOn, OffsetDateTime now,
            LocalDate today) {
        int id = jdbc.queryForObject("""
                INSERT INTO conta_pagar
                    (owner_user_id, categoria_id, entidade_id, descricao, necessidade,
                     valor, data_vencimento, criada_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Integer.class, ownerId, categoryId, entityId, description,
                necessity, amount, dueOn, now);
        return find(ownerId, id, today).orElseThrow(Payables.NotFound::new);
    }

    @Override
    public Optional<Payable> find(int ownerId, int id, LocalDate today) {
        return jdbc.query(SELECT_PAYABLES + """
                WHERE o.owner_user_id = ? AND o.id = ?
                """, (rs, row) -> toPayable(rs, today), ownerId, id).stream().findFirst();
    }

    @Override
    public List<Payable> list(int ownerId, LocalDate month, LocalDate today) {
        return jdbc.query(SELECT_PAYABLES + """
                WHERE o.owner_user_id = ?
                    AND ((o.data_vencimento >= ? AND o.data_vencimento < ?)
                        OR (o.data_vencimento < ? AND o.cancelada_em IS NULL AND p.id IS NULL))
                ORDER BY o.data_vencimento, o.id
                """, (rs, row) -> toPayable(rs, today), ownerId, month,
                month.plusMonths(1), today);
    }

    @Override
    public void lock(int ownerId, int id) {
        if (jdbc.query("""
                SELECT id FROM conta_pagar
                WHERE owner_user_id = ? AND id = ? FOR UPDATE
                """, (rs, row) -> rs.getInt(1), ownerId, id).isEmpty()) {
            throw new Payables.NotFound();
        }
    }

    @Override
    public Payable update(int ownerId, int id, int categoryId, Integer entityId,
            String description, String necessity, BigDecimal amount, LocalDate dueOn,
            LocalDate today) {
        jdbc.update("""
                UPDATE conta_pagar SET categoria_id = ?, entidade_id = ?, descricao = ?,
                    necessidade = ?, valor = ?, data_vencimento = ?
                WHERE owner_user_id = ? AND id = ?
                """, categoryId, entityId, description, necessity, amount, dueOn, ownerId, id);
        return find(ownerId, id, today).orElseThrow(Payables.NotFound::new);
    }

    @Override
    public void cancel(int ownerId, int id, OffsetDateTime now) {
        jdbc.update("""
                UPDATE conta_pagar SET cancelada_em = ?
                WHERE owner_user_id = ? AND id = ?
                """, now, ownerId, id);
    }

    @Override
    public PayablePayment pay(int ownerId, int id, int walletId, BigDecimal amount,
            LocalDate paidOn, OffsetDateTime now) {
        int paymentId = jdbc.queryForObject("""
                INSERT INTO pagamento_conta_pagar
                    (owner_user_id, conta_pagar_id, conta_financeira_id, valor, pago_em, criado_em)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Integer.class, ownerId, id, walletId, amount, paidOn, now);
        return payment(ownerId, id, paymentId).orElseThrow(Payables.NotFound::new);
    }

    @Override
    public Optional<PayablePayment> payment(int ownerId, int payableId, int paymentId) {
        return jdbc.query("""
                SELECT id, owner_user_id, conta_pagar_id, conta_financeira_id, valor,
                    pago_em, criado_em, estornado_em, motivo_estorno
                FROM pagamento_conta_pagar
                WHERE owner_user_id = ? AND conta_pagar_id = ? AND id = ?
                """, (rs, row) -> toPayment(rs), ownerId, payableId, paymentId)
                .stream().findFirst();
    }

    @Override
    public List<PayablePayment> payments(int ownerId, int payableId) {
        return jdbc.query("""
                SELECT id, owner_user_id, conta_pagar_id, conta_financeira_id, valor,
                    pago_em, criado_em, estornado_em, motivo_estorno
                FROM pagamento_conta_pagar
                WHERE owner_user_id = ? AND conta_pagar_id = ?
                ORDER BY id
                """, (rs, row) -> toPayment(rs), ownerId, payableId);
    }

    @Override
    public void reverse(int ownerId, int paymentId, OffsetDateTime now, String reason) {
        jdbc.update("""
                UPDATE pagamento_conta_pagar SET estornado_em = ?, motivo_estorno = ?
                WHERE owner_user_id = ? AND id = ? AND estornado_em IS NULL
                """, now, reason, ownerId, paymentId);
    }

    private static Payable toPayable(ResultSet rs, LocalDate today) throws SQLException {
        LocalDate due = rs.getObject(10, LocalDate.class);
        String status = rs.getObject(11) != null ? "CANCELADA"
                : rs.getBoolean(13) ? "QUITADA"
                : due.isBefore(today) ? "ATRASADA" : "PENDENTE";
        return new Payable(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                rs.getString(4), rs.getObject(5, Integer.class), rs.getString(6),
                rs.getString(7), Necessity.valueOf(rs.getString(8)), rs.getBigDecimal(9),
                due, status, rs.getObject(12, OffsetDateTime.class));
    }

    private static PayablePayment toPayment(ResultSet rs) throws SQLException {
        return new PayablePayment(rs.getInt(1), rs.getInt(2), rs.getInt(3),
                rs.getInt(4), rs.getBigDecimal(5), rs.getObject(6, LocalDate.class),
                rs.getObject(7, OffsetDateTime.class),
                rs.getObject(8, OffsetDateTime.class), rs.getString(9));
    }
}
