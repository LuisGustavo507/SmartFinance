package project.smartfinance.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.counterparty.CounterpartyStore;
import project.smartfinance.domain.counterparty.Counterparty;
import project.smartfinance.infrastructure.persistence.generated.Entidade;

@Repository
class JpaCounterpartyStore implements CounterpartyStore {

    private final EntityManager entityManager;

    JpaCounterpartyStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Counterparty create(int ownerUserId, String name) {
        var entity = new Entidade();
        entity.setOwnerUserId(ownerUserId);
        entity.setNome(name);
        entity.setAtiva(true);
        entity.setCriadaEm(OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public List<Counterparty> listForOwner(int ownerUserId) {
        return entityManager.createQuery("""
                select e from Entidade e
                where e.ownerUserId = :ownerId
                order by e.nome, e.id
                """, Entidade.class)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Counterparty> findForOwner(int id, int ownerUserId) {
        return findEntityForOwner(id, ownerUserId).map(this::toDomain);
    }

    @Override
    public Optional<Counterparty> setActive(int id, int ownerUserId, boolean active) {
        return findEntityForOwner(id, ownerUserId).map(entity -> {
            entity.setAtiva(active);
            return toDomain(entity);
        });
    }

    @Override
    public Optional<Counterparty> rename(int id, int ownerUserId, String name) {
        return findEntityForOwner(id, ownerUserId).map(entity -> {
            entity.setNome(name);
            entityManager.flush();
            return toDomain(entity);
        });
    }

    @Override
    public boolean deleteIfUnlinked(int id, int ownerUserId) {
        return entityManager.createNativeQuery("""
                DELETE FROM entidade e
                WHERE e.id = :id AND e.owner_user_id = :ownerId
                    AND NOT EXISTS (
                        SELECT 1 FROM lancamento_financeiro l
                        WHERE l.owner_user_id = e.owner_user_id
                            AND l.entidade_id = e.id
                    )
                """)
                .setParameter("id", id)
                .setParameter("ownerId", ownerUserId)
                .executeUpdate() == 1;
    }

    private Optional<Entidade> findEntityForOwner(int id, int ownerUserId) {
        return entityManager.createQuery("""
                select e from Entidade e
                where e.id = :id and e.ownerUserId = :ownerId
                """, Entidade.class)
                .setParameter("id", id)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .findFirst();
    }

    private Counterparty toDomain(Entidade entity) {
        return new Counterparty(entity.getId(), entity.getOwnerUserId(),
                entity.getNome(), entity.isAtiva());
    }
}
