package project.smartfinance.application.card;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import project.smartfinance.domain.card.CardPayment;

public interface CardPaymentStore {

    CardPayment create(int ownerUserId, int statementId, int walletId, BigDecimal amount,
            OffsetDateTime paidOn, OffsetDateTime createdAt);
}
