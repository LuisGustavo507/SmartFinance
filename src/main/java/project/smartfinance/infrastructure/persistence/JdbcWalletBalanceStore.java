package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.wallet.WalletBalanceStore;

@Repository
class JdbcWalletBalanceStore implements WalletBalanceStore {

    private final JdbcTemplate jdbc;

    JdbcWalletBalanceStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public BigDecimal estimate(int ownerUserId, int walletId) {
        return jdbc.queryForObject("""
                SELECT w.saldo_inicial
                    + COALESCE((
                        SELECT SUM(CASE WHEN c.natureza = 'RECEITA'
                            THEN l.valor ELSE -l.valor END)
                        FROM lancamento_financeiro l
                        JOIN categoria c
                            ON c.owner_user_id = l.owner_user_id
                            AND c.id = l.categoria_id
                        WHERE l.owner_user_id = w.owner_user_id
                            AND l.conta_financeira_id = w.id
                            AND l.status = 'EFETIVADO'
                    ), 0)
                    - COALESCE((
                        SELECT SUM(t.valor) FROM transferencia t
                        WHERE t.owner_user_id = w.owner_user_id
                            AND t.conta_origem_id = w.id
                    ), 0)
                    + COALESCE((
                        SELECT SUM(t.valor) FROM transferencia t
                        WHERE t.owner_user_id = w.owner_user_id
                            AND t.conta_destino_id = w.id
                    ), 0)
                     - COALESCE((
                         SELECT SUM(p.valor) FROM pagamento_fatura p
                         WHERE p.owner_user_id = w.owner_user_id
                             AND p.conta_financeira_id = w.id
                     ), 0)
                     - COALESCE((
                         SELECT SUM(p.valor) FROM pagamento_conta_pagar p
                         WHERE p.owner_user_id = w.owner_user_id
                             AND p.conta_financeira_id = w.id
                             AND p.estornado_em IS NULL
                     ), 0)
                 FROM conta_financeira w
                WHERE w.owner_user_id = ? AND w.id = ?
                """, BigDecimal.class, ownerUserId, walletId);
    }
}
