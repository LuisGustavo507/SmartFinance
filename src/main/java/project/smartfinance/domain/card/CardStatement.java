package project.smartfinance.domain.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record CardStatement(int id, int ownerUserId, int cardId, YearMonth referenceMonth,
        LocalDate closingDate, LocalDate dueDate, String status, BigDecimal totalAmount,
        BigDecimal appliedCredits, BigDecimal projectedCredits, BigDecimal closedAmount,
        BigDecimal paidAmount, LocalDate paidOn,
        List<CardInstallment> installments) {
}
