package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.card.CardPaymentStore;
import project.smartfinance.domain.card.CardPayment;
import project.smartfinance.infrastructure.persistence.generated.PagamentoFatura;

@Repository
class JpaCardPaymentStore implements CardPaymentStore {

    private final EntityManager entityManager;

    JpaCardPaymentStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public CardPayment create(int ownerUserId, int statementId, int walletId,
            BigDecimal amount, OffsetDateTime paidOn, OffsetDateTime createdAt) {
        var entity = new PagamentoFatura();
        entity.setOwnerUserId(ownerUserId);
        entity.setFaturaCartaoId(statementId);
        entity.setContaFinanceiraId(walletId);
        entity.setValor(amount);
        entity.setPagoEm(paidOn);
        entity.setCriadoEm(createdAt);
        entityManager.persist(entity);
        entityManager.flush();
        return new CardPayment(entity.getId(), ownerUserId, statementId, walletId, amount,
                paidOn, createdAt);
    }
}
