package project.smartfinance.application.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.CardStatement;
import project.smartfinance.domain.card.StatementPeriod;

public interface CardPurchaseStore {

    int createPurchase(int ownerUserId, int cardId, int categoryId, Integer entityId,
            String description, BigDecimal totalAmount, LocalDate purchasedOn,
            int installmentCount, String necessity, OffsetDateTime createdAt);

    int findOrCreateStatement(int ownerUserId, int cardId, StatementPeriod period,
            String status, OffsetDateTime createdAt);

    void createInstallment(int ownerUserId, int purchaseId, int statementId, int number,
            BigDecimal amount, OffsetDateTime createdAt);

    CardPurchase findPurchase(int ownerUserId, int purchaseId);

    List<CardStatement> listStatements(int ownerUserId, YearMonth month);

    Optional<CardStatement> findStatement(int ownerUserId, int statementId);

    void markPaid(int ownerUserId, int statementId);

    boolean updateEntityLink(int ownerUserId, int purchaseId, Integer entityId);
}
