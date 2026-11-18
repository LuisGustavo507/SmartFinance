package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.institution.InstitutionStore;
import project.smartfinance.application.wallet.WalletStore;
import project.smartfinance.domain.institution.Institution;
import project.smartfinance.domain.wallet.Wallet;
import project.smartfinance.domain.wallet.WalletType;
import project.smartfinance.infrastructure.persistence.generated.ContaFinanceira;

@Repository
class JpaWalletStore implements WalletStore {

    private final EntityManager entityManager;
    private final InstitutionStore institutions;

    JpaWalletStore(EntityManager entityManager, InstitutionStore institutions) {
        this.entityManager = entityManager;
        this.institutions = institutions;
    }

    @Override
    public Wallet create(int ownerUserId, String name, WalletType type,
            String institutionName, BigDecimal initialBalance,
            LocalDate initialBalanceDate) {
        var entity = new ContaFinanceira();
        entity.setOwnerUserId(ownerUserId);
        entity.setNome(name);
        entity.setTipo(type.name());
        applyInstitution(entity, type, institutionName);
        entity.setSaldoInicial(initialBalance);
        entity.setDataSaldoInicial(initialBalanceDate);
        entity.setAtiva(true);
        entity.setCriadaEm(OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity, institutionName);
    }

    /**
     * A cash wallet has no institution. Any other account points to the
     * catalog: a known institution is referenced by its id, and an unknown
     * name falls back to the OUTRA entry with the informed name.
     */
    private void applyInstitution(ContaFinanceira entity, WalletType type,
            String institutionName) {
        if (type == WalletType.CARTEIRA_DINHEIRO) {
            entity.setInstituicaoId(null);
            entity.setInstituicaoNomeOutro(null);
            return;
        }
        Institution known = institutions.findByName(institutionName);
        if (known != null && !"OUTRA".equals(known.code())) {
            entity.setInstituicaoId(known.id());
            entity.setInstituicaoNomeOutro(null);
            return;
        }
        Institution other = institutions.findByName("Outra instituição");
        if (other == null) {
            throw new IllegalStateException("O catálogo de instituições não contém a entrada OUTRA.");
        }
        entity.setInstituicaoId(other.id());
        entity.setInstituicaoNomeOutro(institutionName);
    }

    @Override
    public List<Wallet> listForOwner(int ownerUserId) {
        return entityManager.createQuery("""
                select c from ContaFinanceira c
                where c.ownerUserId = :ownerId
                order by c.id
                """, ContaFinanceira.class)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Wallet> findForOwner(int id, int ownerUserId) {
        return entityManager.createQuery("""
                select c from ContaFinanceira c
                where c.id = :id and c.ownerUserId = :ownerId
                """, ContaFinanceira.class)
                .setParameter("id", id)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    private Wallet toDomain(ContaFinanceira entity) {
        return toDomain(entity, entity.getInstituicaoNomeOutro());
    }

    private Wallet toDomain(ContaFinanceira entity, String institutionName) {
        String display = entity.getInstituicaoNomeOutro() != null
                ? entity.getInstituicaoNomeOutro()
                : institutionNameOf(entity);
        return new Wallet(entity.getId(), entity.getOwnerUserId(), entity.getNome(),
                WalletType.valueOf(entity.getTipo()), display, entity.getSaldoInicial(),
                entity.getDataSaldoInicial(), entity.isAtiva(),
                entity.getSaldoInicial());
    }

    private String institutionNameOf(ContaFinanceira entity) {
        if (entity.getInstituicaoId() == null) {
            return null;
        }
        return entityManager.createQuery("""
                select i.nome from InstituicaoFinanceira i where i.id = :id
                """, String.class)
                .setParameter("id", entity.getInstituicaoId())
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
