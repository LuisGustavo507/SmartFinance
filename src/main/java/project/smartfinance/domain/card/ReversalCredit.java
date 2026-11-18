package project.smartfinance.domain.card;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReversalCredit(int id, int purchaseId, int cardId, BigDecimal originalAmount,
        BigDecimal availableAmount, OffsetDateTime createdAt) {
}
