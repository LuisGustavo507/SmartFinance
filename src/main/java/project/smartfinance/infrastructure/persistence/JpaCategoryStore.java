package project.smartfinance.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.category.CategoryStore;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.category.Category.Kind;
import project.smartfinance.infrastructure.persistence.generated.Categoria;

@Repository
class JpaCategoryStore implements CategoryStore {

    private final EntityManager entityManager;

    JpaCategoryStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Category create(int ownerUserId, String name, Kind kind) {
        var entity = new Categoria();
        entity.setOwnerUserId(ownerUserId);
        entity.setNome(name);
        entity.setNatureza(kind.name());
        entity.setAtiva(true);
        entity.setCriadaEm(OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public List<Category> listForOwner(int ownerUserId) {
        return entityManager.createQuery("""
                select c from Categoria c
                where c.ownerUserId = :ownerId
                order by c.natureza, c.nome, c.id
                """, Categoria.class)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Category> findForOwner(int id, int ownerUserId) {
        // Serializes registrations with a possible change of nature/deletion:
        // once a purchase/entry has locked this category, its transaction
        // commits before a kind change can check for existing links.
        return entityManager.createQuery("""
                select c from Categoria c
                where c.id = :id and c.ownerUserId = :ownerId
                """, Categoria.class)
                .setParameter("id", id)
                .setParameter("ownerId", ownerUserId)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<Category> setActive(int id, int ownerUserId, boolean active) {
        return entityManager.createQuery("""
                select c from Categoria c
                where c.id = :id and c.ownerUserId = :ownerId
                """, Categoria.class)
                .setParameter("id", id)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .findFirst()
                .map(category -> {
                    category.setAtiva(active);
                    entityManager.flush();
                    return toDomain(category);
                });
    }

    @Override
    public boolean hasEntries(int id, int ownerUserId) {
        Long entries = entityManager.createQuery("""
                select count(l) from LancamentoFinanceiro l
                where l.ownerUserId = :ownerId and l.categoriaId = :id
                """, Long.class)
                .setParameter("ownerId", ownerUserId).setParameter("id", id).getSingleResult();
        Long purchases = entityManager.createQuery("""
                select count(c) from CompraCartao c
                where c.ownerUserId = :ownerId and c.categoriaId = :id
                """, Long.class)
                .setParameter("ownerId", ownerUserId).setParameter("id", id).getSingleResult();
        return entries > 0 || purchases > 0;
    }

    @Override
    public Optional<Category> update(int id, int ownerUserId, String name, Kind kind) {
        return entityManager.createQuery("""
                select c from Categoria c where c.id = :id and c.ownerUserId = :ownerId
                """, Categoria.class)
                .setParameter("id", id).setParameter("ownerId", ownerUserId)
                .getResultStream().findFirst().map(category -> {
                    category.setNome(name);
                    category.setNatureza(kind.name());
                    entityManager.flush();
                    return toDomain(category);
                });
    }

    @Override
    public boolean delete(int id, int ownerUserId) {
        int count = entityManager.createQuery("""
                delete from Categoria c where c.id = :id and c.ownerUserId = :ownerId
                """)
                .setParameter("id", id).setParameter("ownerId", ownerUserId)
                .executeUpdate();
        return count > 0;
    }

    private Category toDomain(Categoria entity) {
        return new Category(entity.getId(), entity.getOwnerUserId(), entity.getNome(),
                Kind.valueOf(entity.getNatureza()), entity.isAtiva());
    }
}
