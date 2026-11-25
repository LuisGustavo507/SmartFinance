package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.entry.EntryAlreadyProcessedException;
import project.smartfinance.application.entry.EntryReferenceNotFoundException;
import project.smartfinance.application.entry.EntryStore;
import project.smartfinance.domain.category.Category.Kind;
import project.smartfinance.domain.entry.Entry;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.infrastructure.persistence.generated.Categoria;
import project.smartfinance.infrastructure.persistence.generated.Entidade;
import project.smartfinance.infrastructure.persistence.generated.LancamentoFinanceiro;

@Repository
class JpaEntryStore implements EntryStore {

    private final EntityManager entityManager;

    JpaEntryStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Entry create(int ownerUserId, int walletId, int categoryId, Kind kind,
            String description, BigDecimal amount, LocalDate occurredOn,
            OffsetDateTime recordedAt, Integer entityId, String entityName, Necessity necessity) {
        var entity = new LancamentoFinanceiro();
        entity.setOwnerUserId(ownerUserId);
        entity.setContaFinanceiraId(walletId);
        entity.setCategoriaId(categoryId);
        entity.setEntidadeId(entityId);
        entity.setNecessidade(necessity == null ? null : necessity.name());
        entity.setDescricao(description);
        entity.setValor(amount);
        entity.setDataOcorrencia(occurredOn);
        entity.setMesReferencia(occurredOn.withDayOfMonth(1));
        entity.setStatus("EFETIVADO");
        entity.setCriadoEm(recordedAt);
        entity.setEfetivadoEm(recordedAt);
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity, kind, entityName);
    }

    @Override
    public Entry createPlanned(int ownerUserId, int walletId, int categoryId, Kind kind,
            String description, BigDecimal amount, LocalDate expectedOn,
            OffsetDateTime createdAt, Integer entityId, String entityName, Necessity necessity) {
        var entity = new LancamentoFinanceiro();
        entity.setOwnerUserId(ownerUserId);
        entity.setContaFinanceiraId(walletId);
        entity.setCategoriaId(categoryId);
        entity.setEntidadeId(entityId);
        entity.setNecessidade(necessity == null ? null : necessity.name());
        entity.setDescricao(description);
        entity.setValor(amount);
        entity.setDataPrevista(expectedOn);
        entity.setMesReferencia(expectedOn.withDayOfMonth(1));
        entity.setStatus("PREVISTO");
        entity.setCriadoEm(createdAt);
        entityManager.persist(entity);
        entityManager.flush();
        return toDomain(entity, kind, entityName);
    }

    @Override
    public List<Entry> createSeries(int ownerUserId, int walletId, int categoryId,
            Kind kind, String description, BigDecimal amount,
            List<LocalDate> expectedDates, OffsetDateTime createdAt,
            Integer entityId, String entityName, Necessity necessity) {
        int seriesId = ((Number) entityManager.createNativeQuery(
                "SELECT nextval('lancamento_serie_id_seq')").getSingleResult()).intValue();
        List<Entry> result = new ArrayList<>(expectedDates.size());
        for (int index = 0; index < expectedDates.size(); index++) {
            var entity = new LancamentoFinanceiro();
            entity.setOwnerUserId(ownerUserId);
            entity.setContaFinanceiraId(walletId);
            entity.setCategoriaId(categoryId);
            entity.setEntidadeId(entityId);
            entity.setNecessidade(necessity == null ? null : necessity.name());
            entity.setDescricao(description);
            entity.setValor(amount);
            LocalDate expectedOn = expectedDates.get(index);
            entity.setDataPrevista(expectedOn);
            entity.setMesReferencia(expectedOn.withDayOfMonth(1));
            entity.setStatus("PREVISTO");
            entity.setCriadoEm(createdAt);
            entity.setSerieId(seriesId);
            entity.setNumeroOcorrencia((short) (index + 1));
            entity.setTotalOcorrencias((short) expectedDates.size());
            entityManager.persist(entity);
            result.add(toDomain(entity, kind, entityName));
        }
        entityManager.flush();
        return result;
    }

    @Override
    public List<Entry> listForOwnerAndMonth(int ownerUserId, LocalDate firstDayOfMonth,
            Integer entityId) {
        String query = """
                select l, c.natureza, e.nome
                from LancamentoFinanceiro l
                join Categoria c
                    on c.id = l.categoriaId and c.ownerUserId = l.ownerUserId
                left join Entidade e
                    on e.id = l.entidadeId and e.ownerUserId = l.ownerUserId
                where l.ownerUserId = :ownerId
                      and l.mesReferencia = :month
                      and l.status <> 'CANCELADO'
                """ + (entityId == null ? "" : " and l.entidadeId = :entityId\n")
                + " order by l.dataOcorrencia, l.id";
        var typedQuery = entityManager.createQuery(query, Object[].class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("month", firstDayOfMonth);
        if (entityId != null) {
            typedQuery.setParameter("entityId", entityId);
        }
        return typedQuery
                .getResultStream()
                .map(row -> toDomain((LancamentoFinanceiro) row[0],
                        Kind.valueOf((String) row[1]), (String) row[2]))
                .toList();
    }

    @Override
    public List<Entry> listForOwnerAndSeries(int ownerUserId, int seriesId) {
        return entityManager.createQuery("""
                select l, c.natureza, e.nome
                from LancamentoFinanceiro l
                join Categoria c
                    on c.id = l.categoriaId and c.ownerUserId = l.ownerUserId
                left join Entidade e
                    on e.id = l.entidadeId and e.ownerUserId = l.ownerUserId
                where l.ownerUserId = :ownerId and l.serieId = :seriesId
                order by l.numeroOcorrencia
                """, Object[].class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("seriesId", seriesId)
                .getResultStream()
                .map(row -> toDomain((LancamentoFinanceiro) row[0],
                        Kind.valueOf((String) row[1]), (String) row[2]))
                .toList();
    }

    @Override
    public Optional<Entry> findForOwner(int ownerUserId, int id) {
        return entityManager.createQuery("""
                select l, c.natureza, e.nome
                from LancamentoFinanceiro l
                join Categoria c
                    on c.id = l.categoriaId and c.ownerUserId = l.ownerUserId
                left join Entidade e
                    on e.id = l.entidadeId and e.ownerUserId = l.ownerUserId
                where l.ownerUserId = :ownerId and l.id = :id
                """, Object[].class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("id", id)
                .getResultStream()
                .findFirst()
                .map(row -> toDomain((LancamentoFinanceiro) row[0],
                        Kind.valueOf((String) row[1]), (String) row[2]));
    }

    @Override
    public Entry updatePlanned(int ownerUserId, int id, int walletId, int categoryId,
            Kind kind, String description, BigDecimal amount, LocalDate expectedOn,
            Integer entityId, String entityName, Necessity necessity) {
        LancamentoFinanceiro entity = entityManager.find(LancamentoFinanceiro.class, id,
                LockModeType.PESSIMISTIC_WRITE);
        if (entity == null || entity.getOwnerUserId() != ownerUserId) {
            throw new EntryReferenceNotFoundException();
        }
        if (!"PREVISTO".equals(entity.getStatus())) {
            throw new EntryAlreadyProcessedException();
        }
        entity.setContaFinanceiraId(walletId);
        entity.setCategoriaId(categoryId);
        entity.setEntidadeId(entityId);
        entity.setNecessidade(necessity == null ? null : necessity.name());
        entity.setDescricao(description);
        entity.setValor(amount);
        entity.setDataPrevista(expectedOn);
        entity.setMesReferencia(expectedOn.withDayOfMonth(1));
        entityManager.flush();
        return toDomain(entity, kind, entityName);
    }

    @Override
    public Entry updateRealized(int ownerUserId, int id, int walletId, int categoryId,
            Kind kind, String description, BigDecimal amount, LocalDate occurredOn,
            Integer entityId, String entityName, Necessity necessity) {
        LancamentoFinanceiro entity = entityManager.find(LancamentoFinanceiro.class, id,
                LockModeType.PESSIMISTIC_WRITE);
        if (entity == null || entity.getOwnerUserId() != ownerUserId) {
            throw new EntryReferenceNotFoundException();
        }
        if (!"EFETIVADO".equals(entity.getStatus())) {
            throw new EntryAlreadyProcessedException();
        }
        entity.setContaFinanceiraId(walletId);
        entity.setCategoriaId(categoryId);
        entity.setEntidadeId(entityId);
        entity.setNecessidade(necessity == null ? null : necessity.name());
        entity.setDescricao(description);
        entity.setValor(amount);
        entity.setDataOcorrencia(occurredOn);
        entity.setMesReferencia(occurredOn.withDayOfMonth(1));
        entityManager.flush();
        return toDomain(entity, kind, entityName);
    }

    @Override
    public Optional<Entry> updateEntityLink(int ownerUserId, int id, Integer entityId) {
        LancamentoFinanceiro entity = entityManager.find(LancamentoFinanceiro.class, id);
        if (entity == null || entity.getOwnerUserId() != ownerUserId) {
            return Optional.empty();
        }
        entity.setEntidadeId(entityId);
        entityManager.flush();
        return findForOwner(ownerUserId, id);
    }

    private Entry toDomain(LancamentoFinanceiro entity, Kind kind, String entityName) {
        return new Entry(entity.getId(), entity.getOwnerUserId(),
                entity.getContaFinanceiraId(), entity.getCategoriaId(), kind,
                entity.getDescricao(), entity.getValor(), entity.getDataOcorrencia(),
                entity.getMesReferencia(), entity.getStatus(), entity.getCriadoEm(),
                entity.getEfetivadoEm(), entity.getDataPrevista(),
                entity.getEntidadeId(), entityName, entity.getSerieId(),
                entity.getNumeroOcorrencia() == null ? null :
                        entity.getNumeroOcorrencia().intValue(),
                entity.getTotalOcorrencias() == null ? null :
                        entity.getTotalOcorrencias().intValue(),
                entity.getNecessidade() == null ? null :
                        Necessity.valueOf(entity.getNecessidade()));
    }
}
