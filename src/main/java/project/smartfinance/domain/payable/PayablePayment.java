package project.smartfinance.domain.payable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PayablePayment(int id, int ownerUserId, int payableId, int walletId,
        BigDecimal amount, LocalDate paidOn, OffsetDateTime createdAt,
        OffsetDateTime reversedAt, String reversalReason) {
}
