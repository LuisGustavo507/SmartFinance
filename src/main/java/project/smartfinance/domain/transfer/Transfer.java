package project.smartfinance.domain.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record Transfer(int id, int ownerUserId, int sourceWalletId,
        int destinationWalletId, BigDecimal amount, String description,
        OffsetDateTime transferredAt, OffsetDateTime createdAt) {
}
