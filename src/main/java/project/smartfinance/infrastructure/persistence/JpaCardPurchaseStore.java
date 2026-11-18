package project.smartfinance.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.card.CardPurchaseStore;
import project.smartfinance.domain.card.CardInstallment;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.CardStatement;
import project.smartfinance.domain.card.StatementPeriod;
import project.smartfinance.infrastructure.persistence.generated.CompraCartao;
import project.smartfinance.infrastructure.persistence.generated.FaturaCartao;
import project.smartfinance.infrastructure.persistence.generated.PagamentoFatura;
import project.smartfinance.infrastructure.persistence.generated.ParcelaCartao;

@Repository
class JpaCardPurchaseStore implements CardPurchaseStore {

    private final EntityManager entityManager;
    private final Clock clock;
    private final JdbcTemplate jdbc;

    JpaCardPurchaseStore(EntityManager entityManager, Clock clock, JdbcTemplate jdbc) {
        this.entityManager = entityManager;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Override
    public int createPurchase(int ownerUserId, int cardId, int categoryId, Integer entityId,
            String description, BigDecimal totalAmount, LocalDate purchasedOn,
            int installmentCount, String necessity, OffsetDateTime createdAt) {
        var entity = new CompraCartao();
        entity.setOwnerUserId(ownerUserId);
        entity.setCartaoId(cardId);
        entity.setCategoriaId(categoryId);
        entity.setEntidadeId(entityId);
        entity.setDescricao(description);
        entity.setValorTotal(totalAmount);
        entity.setCompradaEm(createdAt);
        entity.setQuantidadeParcelas((short) installmentCount);
        entity.setNecessidade(necessity);
        entity.setStatus("ATIVA");
        entity.setCriadoEm(createdAt);
        entityManager.persist(entity);
        entityManager.flush();
        return entity.getId();
    }

    @Override
    public int findOrCreateStatement(int ownerUserId, int cardId, StatementPeriod period,
            String status, OffsetDateTime createdAt) {
        LocalDate referenceMonth = period.referenceMonth().atDay(1);
        FaturaCartao existing = entityManager.createQuery("""
                select f from FaturaCartao f
                where f.ownerUserId = :ownerId and f.cartaoId = :cardId
                      and f.mesReferencia = :month
                """, FaturaCartao.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("cardId", cardId)
                .setParameter("month", referenceMonth)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (existing != null) {
            openStartedCycle(existing);
            return existing.getId();
        }
        var entity = new FaturaCartao();
        entity.setOwnerUserId(ownerUserId);
        entity.setCartaoId(cardId);
        entity.setMesReferencia(referenceMonth);
        entity.setDataFechamento(period.closingDate());
        entity.setDataVencimento(period.dueDate());
        entity.setStatus(status);
        entity.setCriadoEm(createdAt);
        entityManager.persist(entity);
        entityManager.flush();
        return entity.getId();
    }

    @Override
    public void createInstallment(int ownerUserId, int purchaseId, int statementId,
            int number, BigDecimal amount, OffsetDateTime createdAt) {
        var entity = new ParcelaCartao();
        entity.setOwnerUserId(ownerUserId);
        entity.setCompraCartaoId(purchaseId);
        entity.setFaturaCartaoId(statementId);
        entity.setNumeroParcela((short) number);
        entity.setValor(amount);
        entity.setStatus("ATIVA");
        entity.setCriadaEm(createdAt);
        entityManager.persist(entity);
    }

    @Override
    public CardPurchase findPurchase(int ownerUserId, int purchaseId) {
        CompraCartao entity = entityManager.createQuery("""
                select c from CompraCartao c
                where c.ownerUserId = :ownerId and c.id = :id
                """, CompraCartao.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("id", purchaseId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (entity == null) {
            return null;
        }
        return toPurchase(entity, installmentsOfPurchase(ownerUserId, purchaseId));
    }

    @Override
    public List<CardStatement> listStatements(int ownerUserId, YearMonth month) {
        LocalDate referenceMonth = month.atDay(1);
        List<FaturaCartao> statements = entityManager.createQuery("""
                select f from FaturaCartao f
                where f.ownerUserId = :ownerId and f.mesReferencia = :month
                order by f.cartaoId, f.id
                """, FaturaCartao.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("month", referenceMonth)
                .getResultStream()
                .toList();
        return statements.stream()
                .map(statement -> {
                    openStartedCycle(statement);
                    List<CardInstallment> installments = installmentsOfStatement(ownerUserId,
                            statement.getId());
                    return toStatement(statement, installments);
                })
                .toList();
    }

    @Override
    public Optional<CardStatement> findStatement(int ownerUserId, int statementId) {
        FaturaCartao statement = entityManager.createQuery("""
                select f from FaturaCartao f
                where f.ownerUserId = :ownerId and f.id = :id
                """, FaturaCartao.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("id", statementId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (statement == null) {
            return Optional.empty();
        }
        openStartedCycle(statement);
        return Optional.of(toStatement(statement,
                installmentsOfStatement(ownerUserId, statementId)));
    }

    @Override
    public void markPaid(int ownerUserId, int statementId) {
        FaturaCartao statement = entityManager.find(FaturaCartao.class, statementId);
        if (statement == null || statement.getOwnerUserId() != ownerUserId) {
            return;
        }
        if ("FECHADA".equals(statement.getStatus()) || "ATRASADA".equals(statement.getStatus())) {
            statement.setStatus("PAGA");
            entityManager.flush();
        }
    }

    private void openStartedCycle(FaturaCartao statement) {
        if (!"PROJETADA".equals(statement.getStatus())) {
            return;
        }
        LocalDate cycleStart = statement.getDataFechamento().minusMonths(1).plusDays(1);
        if (!cycleStart.isAfter(LocalDate.now(clock))) {
            statement.setStatus("ABERTA");
            entityManager.flush();
        }
    }

    @Override
    public boolean updateEntityLink(int ownerUserId, int purchaseId, Integer entityId) {
        CompraCartao purchase = entityManager.createQuery("""
                select c from CompraCartao c
                where c.id = :id and c.ownerUserId = :ownerId
                """, CompraCartao.class)
                .setParameter("id", purchaseId)
                .setParameter("ownerId", ownerUserId)
                .getResultStream()
                .findFirst()
                .orElse(null);
        if (purchase == null) {
            return false;
        }
        purchase.setEntidadeId(entityId);
        entityManager.flush();
        return true;
    }

    private List<CardInstallment> installmentsOfPurchase(int ownerUserId, int purchaseId) {
        return entityManager.createQuery("""
                select p, c.descricao, c.categoriaId, c.entidadeId, cat.nome, e.nome,
                       f.dataVencimento, f.mesReferencia
                from ParcelaCartao p
                join CompraCartao c
                    on c.id = p.compraCartaoId and c.ownerUserId = p.ownerUserId
                join Categoria cat
                    on cat.id = c.categoriaId and cat.ownerUserId = c.ownerUserId
                join FaturaCartao f
                    on f.id = p.faturaCartaoId and f.ownerUserId = p.ownerUserId
                left join Entidade e
                    on e.id = c.entidadeId and e.ownerUserId = c.ownerUserId
                where p.ownerUserId = :ownerId and p.compraCartaoId = :purchaseId
                order by p.numeroParcela
                """, Object[].class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("purchaseId", purchaseId)
                .getResultStream()
                .map(row -> toInstallment((ParcelaCartao) row[0], (String) row[1],
                        (Integer) row[2], (Integer) row[3], (String) row[4], (String) row[5],
                        (LocalDate) row[6], (LocalDate) row[7]))
                .toList();
    }

    private List<CardInstallment> installmentsOfStatement(int ownerUserId, int statementId) {
        return entityManager.createQuery("""
                select p, c.descricao, c.categoriaId, c.entidadeId, cat.nome, e.nome,
                       f.dataVencimento, f.mesReferencia
                from ParcelaCartao p
                join CompraCartao c
                    on c.id = p.compraCartaoId and c.ownerUserId = p.ownerUserId
                join Categoria cat
                    on cat.id = c.categoriaId and cat.ownerUserId = c.ownerUserId
                join FaturaCartao f
                    on f.id = p.faturaCartaoId and f.ownerUserId = p.ownerUserId
                left join Entidade e
                    on e.id = c.entidadeId and e.ownerUserId = c.ownerUserId
                where p.ownerUserId = :ownerId and p.faturaCartaoId = :statementId
                order by p.numeroParcela
                """, Object[].class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("statementId", statementId)
                .getResultStream()
                .map(row -> toInstallment((ParcelaCartao) row[0], (String) row[1],
                        (Integer) row[2], (Integer) row[3], (String) row[4], (String) row[5],
                        (LocalDate) row[6], (LocalDate) row[7]))
                .toList();
    }

    private CardInstallment toInstallment(ParcelaCartao entity, String description,
            Integer categoryId, Integer entityId, String categoryName, String entityName,
            LocalDate statementDueDate, LocalDate statementReferenceMonth) {
        return new CardInstallment(entity.getId(), entity.getCompraCartaoId(),
                entity.getFaturaCartaoId(), entity.getNumeroParcela(), entity.getValor(),
                entity.getStatus(), description, categoryId == null ? 0 : categoryId,
                categoryName, entityId, entityName, statementDueDate,
                statementReferenceMonth == null ? null : YearMonth.from(statementReferenceMonth).toString());
    }

    private CardPurchase toPurchase(CompraCartao entity, List<CardInstallment> installments) {
        return new CardPurchase(entity.getId(), entity.getOwnerUserId(), entity.getCartaoId(),
                entity.getCategoriaId(), entity.getEntidadeId(), entity.getDescricao(),
                entity.getValorTotal(),
                entity.getCompradaEm().atZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
                entity.getQuantidadeParcelas(), entity.getNecessidade(), entity.getStatus(),
                installments);
    }

    private CardStatement toStatement(FaturaCartao entity,
            List<CardInstallment> installments) {
        BigDecimal total = installments.stream()
                .filter(installment -> !"CANCELADA".equals(installment.status()))
                .map(CardInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal applied = (BigDecimal) entityManager.createQuery("""
                select coalesce(sum(a.valor), 0) from AplicacaoCredito a
                where a.ownerUserId = :ownerId and a.faturaCartaoId = :statementId
                """)
                .setParameter("ownerId", entity.getOwnerUserId())
                .setParameter("statementId", entity.getId())
                .getSingleResult();
        Payment payment = paymentOf(entity.getOwnerUserId(), entity.getId());
        BigDecimal forecast = ("ABERTA".equals(entity.getStatus())
                || "PROJETADA".equals(entity.getStatus()))
                ? forecastFor(entity) : applied;
        return new CardStatement(entity.getId(), entity.getOwnerUserId(), entity.getCartaoId(),
                YearMonth.from(entity.getMesReferencia()), entity.getDataFechamento(),
                entity.getDataVencimento(), entity.getStatus(), total, applied, forecast,
                entity.getValorFechado(), payment.amount(), payment.paidOn(), installments);
    }

    private BigDecimal forecastFor(FaturaCartao target) {
        List<ForecastCredit> credits = new ArrayList<>(jdbc.query("""
                SELECT c.criado_em, c.valor_original - COALESCE(SUM(a.valor), 0)
                FROM credito_estorno c
                JOIN compra_cartao p ON p.id = c.compra_cartao_id
                    AND p.owner_user_id = c.owner_user_id
                LEFT JOIN aplicacao_credito a ON a.credito_estorno_id = c.id
                WHERE c.owner_user_id = ? AND p.cartao_id = ?
                GROUP BY c.id
                HAVING c.valor_original > COALESCE(SUM(a.valor), 0)
                ORDER BY c.criado_em, c.id
                """, (rs, row) -> new ForecastCredit(
                rs.getObject(1, OffsetDateTime.class), rs.getBigDecimal(2)),
                target.getOwnerUserId(), target.getCartaoId()));
        List<ForecastStatement> statements = jdbc.query("""
                SELECT f.id, f.data_fechamento, COALESCE(SUM(p.valor)
                    FILTER (WHERE p.status = 'ATIVA'), 0)
                FROM fatura_cartao f
                LEFT JOIN parcela_cartao p ON p.fatura_cartao_id = f.id
                WHERE f.owner_user_id = ? AND f.cartao_id = ?
                    AND f.status IN ('ABERTA', 'PROJETADA')
                    AND f.data_fechamento <= ?
                GROUP BY f.id
                ORDER BY f.data_fechamento, f.id
                """, (rs, row) -> new ForecastStatement(rs.getInt(1),
                rs.getDate(2).toLocalDate(), rs.getBigDecimal(3)),
                target.getOwnerUserId(), target.getCartaoId(), target.getDataFechamento());
        BigDecimal forecast = BigDecimal.ZERO;
        for (ForecastStatement statement : statements) {
            BigDecimal left = statement.gross();
            BigDecimal appliedHere = BigDecimal.ZERO;
            for (int i = 0; i < credits.size() && left.signum() > 0; i++) {
                ForecastCredit credit = credits.get(i);
                if (!credit.createdAt().toInstant().isBefore(
                        statement.closingDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC))) {
                    continue;
                }
                BigDecimal used = left.min(credit.available());
                left = left.subtract(used);
                appliedHere = appliedHere.add(used);
                credits.set(i, new ForecastCredit(credit.createdAt(),
                        credit.available().subtract(used)));
            }
            if (statement.id() == target.getId()) {
                forecast = appliedHere;
                break;
            }
        }
        return forecast;
    }

    private record ForecastCredit(OffsetDateTime createdAt, BigDecimal available) {
    }

    private record ForecastStatement(int id, LocalDate closingDate, BigDecimal gross) {
    }

    private Payment paymentOf(int ownerUserId, int statementId) {
        List<PagamentoFatura> payments = entityManager.createQuery("""
                select p from PagamentoFatura p
                where p.ownerUserId = :ownerId and p.faturaCartaoId = :statementId
                order by p.pagoEm, p.id
                """, PagamentoFatura.class)
                .setParameter("ownerId", ownerUserId)
                .setParameter("statementId", statementId)
                .getResultStream()
                .toList();
        if (payments.isEmpty()) {
            return new Payment(BigDecimal.ZERO, null);
        }
        BigDecimal amount = payments.stream()
                .map(PagamentoFatura::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate paidOn = payments.get(0).getPagoEm().atZoneSameInstant(ZoneOffset.UTC)
                .toLocalDate();
        return new Payment(amount, paidOn);
    }

    private record Payment(BigDecimal amount, LocalDate paidOn) {
    }
}
