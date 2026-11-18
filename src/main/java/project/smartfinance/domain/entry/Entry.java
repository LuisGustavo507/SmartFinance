package project.smartfinance.domain.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import project.smartfinance.domain.category.Category.Kind;
import project.smartfinance.domain.expense.Necessity;

public record Entry(int id, int ownerUserId, int walletId, int categoryId,
        Kind kind, String description, BigDecimal amount, LocalDate occurredOn,
        LocalDate referenceMonth, String status, OffsetDateTime createdAt,
        OffsetDateTime effectiveAt, LocalDate expectedOn, Integer entityId,
        String entityName, Integer seriesId, Integer occurrenceNumber,
        Integer totalOccurrences, Necessity necessity) {
}
