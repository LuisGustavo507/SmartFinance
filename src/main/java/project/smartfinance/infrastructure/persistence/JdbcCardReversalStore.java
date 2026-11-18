package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.card.CardPurchaseStore;
import project.smartfinance.application.card.CardReversalStore;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.ReversalCredit;

@Repository
class JdbcCardReversalStore implements CardReversalStore {

    private final JdbcTemplate jdbc;
    private final CardPurchaseStore purchases;

    JdbcCardReversalStore(JdbcTemplate jdbc, CardPurchaseStore purchases) {
        this.jdbc = jdbc;
        this.purchases = purchases;
    }

    @Override
    public Optional<CardPurchase> reverse(int ownerUserId, int purchaseId, OffsetDateTime now) {
        List<Integer> owned = jdbc.query("""
                SELECT id FROM compra_cartao
                WHERE id = ? AND owner_user_id = ?
                FOR UPDATE
                """, (rs, row) -> rs.getInt(1), purchaseId, ownerUserId);
        if (owned.isEmpty()) {
            return Optional.empty();
        }
        List<Part> parts = jdbc.query("""
                SELECT p.id, p.valor, f.status
                FROM parcela_cartao p
                JOIN fatura_cartao f ON f.id = p.fatura_cartao_id
                    AND f.owner_user_id = p.owner_user_id
                WHERE p.owner_user_id = ? AND p.compra_cartao_id = ?
                    AND p.status = 'ATIVA'
                ORDER BY p.id
                FOR UPDATE OF p
                """, (rs, row) -> new Part(rs.getInt(1), rs.getBigDecimal(2),
                rs.getString(3)), ownerUserId, purchaseId);
        if (parts.isEmpty()) {
            return Optional.of(purchases.findPurchase(ownerUserId, purchaseId));
        }
        BigDecimal credit = BigDecimal.ZERO;
        for (Part part : parts) {
            if (part.status().equals("PROJETADA") || part.status().equals("ABERTA")) {
                jdbc.update("UPDATE parcela_cartao SET status = 'CANCELADA' WHERE id = ?",
                        part.id());
            } else {
                jdbc.update("UPDATE parcela_cartao SET status = 'ESTORNADA' WHERE id = ?",
                        part.id());
                credit = credit.add(part.amount());
            }
        }
        jdbc.update("UPDATE compra_cartao SET status = ? WHERE id = ? AND owner_user_id = ?",
                credit.signum() == 0 ? "CANCELADA" : "ESTORNADA", purchaseId, ownerUserId);
        if (credit.signum() > 0) {
            jdbc.update("""
                    INSERT INTO credito_estorno
                        (owner_user_id, compra_cartao_id, valor_original, criado_em)
                    VALUES (?, ?, ?, ?)
                    """, ownerUserId, purchaseId, credit, now);
        }
        // JPA may have already loaded the purchase in this transaction; the
        // reversal uses JDBC, so refresh the persistence context before reading.
        return Optional.of(purchases.findPurchase(ownerUserId, purchaseId));
    }

    @Override
    public List<ReversalCredit> credits(int ownerUserId, YearMonth month) {
        return jdbc.query("""
                SELECT c.id, c.compra_cartao_id, p.cartao_id, c.valor_original,
                    c.valor_original - COALESCE(SUM(a.valor), 0), c.criado_em
                FROM credito_estorno c
                JOIN compra_cartao p ON p.id = c.compra_cartao_id
                    AND p.owner_user_id = c.owner_user_id
                LEFT JOIN aplicacao_credito a ON a.credito_estorno_id = c.id
                WHERE c.owner_user_id = ?
                    AND c.criado_em >= ? AND c.criado_em < ?
                GROUP BY c.id, p.cartao_id
                ORDER BY c.criado_em, c.id
                """, (rs, row) -> new ReversalCredit(rs.getInt(1), rs.getInt(2),
                rs.getInt(3), rs.getBigDecimal(4), rs.getBigDecimal(5),
                rs.getObject(6, OffsetDateTime.class)),
                ownerUserId, month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC),
                month.plusMonths(1).atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC));
    }

    private record Part(int id, BigDecimal amount, String status) {
    }
}
