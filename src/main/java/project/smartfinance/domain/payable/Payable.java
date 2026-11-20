package project.smartfinance.domain.payable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import project.smartfinance.domain.expense.Necessity;

public record Payable(int id, int ownerUserId, int categoryId, String categoryName,
        Integer entityId, String entityName, String description, Necessity necessity,
        BigDecimal amount, LocalDate dueOn, String status, OffsetDateTime createdAt) {
}
