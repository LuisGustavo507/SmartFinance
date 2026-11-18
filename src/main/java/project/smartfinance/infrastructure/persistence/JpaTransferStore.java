package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.transfer.TransferStore;
import project.smartfinance.domain.transfer.Transfer;
import project.smartfinance.infrastructure.persistence.generated.Transferencia;

@Repository
class JpaTransferStore implements TransferStore {

    private final EntityManager entityManager;

    JpaTransferStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Transfer create(int ownerUserId, int sourceWalletId, int destinationWalletId,
            BigDecimal amount, String description, OffsetDateTime transferredAt,
            OffsetDateTime createdAt) {
        var entity = new Transferencia();
        entity.setOwnerUserId(ownerUserId);
        entity.setContaOrigemId(sourceWalletId);
        entity.setContaDestinoId(destinationWalletId);
        entity.setValor(amount);
        entity.setDescricao(description);
        entity.setTransferidaEm(transferredAt);
        entity.setCriadaEm(createdAt);
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public List<Transfer> listForOwnerBetween(int ownerUserId, OffsetDateTime from,
            OffsetDateTime untilExclusive) {
        return entityManager.createQuery("""
                select t from Transferencia t
                where t.ownerUserId = :ownerId
                      and t.transferidaEm >= :from
                      and t.transferidaEm < :until
                order by t.transferidaEm, t.id
                """, Transferencia.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("from", from)
                .setParameter("until", untilExclusive)
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    private Transfer toDomain(Transferencia entity) {
        return new Transfer(entity.getId(), entity.getOwnerUserId(),
                entity.getContaOrigemId(), entity.getContaDestinoId(),
                entity.getValor(), entity.getDescricao(), entity.getTransferidaEm(),
                entity.getCriadaEm());
    }
}
