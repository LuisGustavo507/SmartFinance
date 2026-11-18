package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.card.CreditCardStore;
import project.smartfinance.application.institution.InstitutionStore;
import project.smartfinance.application.card.CardCycleLockedException;
import project.smartfinance.domain.card.CreditCard;
import project.smartfinance.infrastructure.persistence.generated.CartaoCredito;
import project.smartfinance.infrastructure.persistence.generated.InstituicaoFinanceira;

@Repository
class JpaCreditCardStore implements CreditCardStore {

    private static final String OTHER_INSTITUTION_CODE = "OUTRA";

    private final EntityManager entityManager;
    private final InstitutionStore institutions;

    private Integer otherInstitutionId;

    JpaCreditCardStore(EntityManager entityManager, InstitutionStore institutions) {
        this.entityManager = entityManager;
        this.institutions = institutions;
    }

    @Override
    public CreditCard create(int ownerUserId, String name, String institutionName,
            int closingDay, int dueDay, BigDecimal creditLimit,
            Integer defaultPaymentWalletId) {
        var entity = new CartaoCredito();
        entity.setOwnerUserId(ownerUserId);
        entity.setContaPagamentoPadraoId(defaultPaymentWalletId);
        entity.setNome(name);
        applyInstitution(entity, institutionName);
        entity.setDiaFechamento((short) closingDay);
        entity.setDiaVencimento((short) dueDay);
        entity.setLimiteTotal(creditLimit);
        entity.setAtivo(true);
        entity.setCriadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity);
    }

    @Override
    public List<CreditCard> listForOwner(int ownerUserId) {
        return entityManager.createQuery("""
                select c from CartaoCredito c
                where c.ownerUserId = :ownerId
                order by c.id
                """, CartaoCredito.class)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<CreditCard> findForOwner(int id, int ownerUserId) {
        return entityManager.createQuery("""
                select c from CartaoCredito c
                where c.ownerUserId = :ownerId and c.id = :id
                """, CartaoCredito.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("id", id)
                .getResultStream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public Optional<CreditCard> update(int id, int ownerUserId, String name, String institutionName,
            int closingDay, int dueDay, BigDecimal creditLimit, Integer defaultPaymentWalletId) {
        CartaoCredito card = findEntity(id, ownerUserId);
        if (card == null) {
            return Optional.empty();
        }
        if (card.getDiaFechamento() != closingDay || card.getDiaVencimento() != dueDay) {
            boolean hasHistory = !entityManager.createQuery("""
                    select c.id from CompraCartao c
                    where c.ownerUserId = :ownerId and c.cartaoId = :cardId
                    """, Integer.class)
                    .setParameter("ownerId", ownerUserId)
                    .setParameter("cardId", id)
                    .setMaxResults(1)
                    .getResultList().isEmpty()
                    || !entityManager.createQuery("""
                    select f.id from FaturaCartao f
                    where f.ownerUserId = :ownerId and f.cartaoId = :cardId
                    """, Integer.class)
                    .setParameter("ownerId", ownerUserId)
                    .setParameter("cardId", id)
                    .setMaxResults(1)
                    .getResultList().isEmpty();
            if (hasHistory) {
                throw new CardCycleLockedException();
            }
        }
        card.setNome(name);
        applyInstitution(card, institutionName);
        card.setDiaFechamento((short) closingDay);
        card.setDiaVencimento((short) dueDay);
        card.setLimiteTotal(creditLimit);
        card.setContaPagamentoPadraoId(defaultPaymentWalletId);
        entityManager.flush();
        return Optional.of(toDomain(card));
    }

    @Override
    public Optional<CreditCard> setActive(int id, int ownerUserId, boolean active) {
        CartaoCredito card = findEntity(id, ownerUserId);
        if (card == null) {
            return Optional.empty();
        }
        card.setAtivo(active);
        entityManager.flush();
        return Optional.of(toDomain(card));
    }

    private CartaoCredito findEntity(int id, int ownerUserId) {
        return entityManager.createQuery("""
                select c from CartaoCredito c
                where c.ownerUserId = :ownerId and c.id = :id
                """, CartaoCredito.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("id", id)
                .getResultStream().findFirst().orElse(null);
    }

    private CreditCard toDomain(CartaoCredito entity) {
        return new CreditCard(entity.getId(), entity.getOwnerUserId(), entity.getNome(),
                institutionName(entity), entity.getDiaFechamento(), entity.getDiaVencimento(),
                entity.getLimiteTotal(), entity.getContaPagamentoPadraoId(), entity.isAtivo());
    }

    private String institutionName(CartaoCredito entity) {
        if (entity.getInstituicaoNomeOutro() != null) {
            return entity.getInstituicaoNomeOutro();
        }
        return entityManager.createQuery("""
                select i.nome from InstituicaoFinanceira i where i.id = :id
                """, String.class)
                .setParameter("id", entity.getInstituicaoId())
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    private int otherInstitutionId() {
        if (otherInstitutionId == null) {
            otherInstitutionId = entityManager.createQuery("""
                    select i.id from InstituicaoFinanceira i
                    where i.codigo = :code
                    """, Integer.class)
                    .setParameter("code", OTHER_INSTITUTION_CODE)
                    .getResultStream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Instituição OUTRA ausente no catálogo."));
        }
        return otherInstitutionId;
    }

    /**
     * A catalog institution is referenced by its id; an unknown name falls
     * back to the OUTRA entry with the informed name.
     */
    private void applyInstitution(CartaoCredito card, String institutionName) {
        var known = institutions.findByName(institutionName);
        if (known != null && !OTHER_INSTITUTION_CODE.equals(known.code())) {
            card.setInstituicaoId(known.id());
            card.setInstituicaoNomeOutro(null);
            return;
        }
        card.setInstituicaoId(otherInstitutionId());
        card.setInstituicaoNomeOutro(institutionName);
    }
}
