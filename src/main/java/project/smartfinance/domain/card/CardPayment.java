package project.smartfinance.domain.card;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CardPayment(int id, int ownerUserId, int statementId, int walletId,
        BigDecimal amount, OffsetDateTime paidOn, OffsetDateTime createdAt) {
}
