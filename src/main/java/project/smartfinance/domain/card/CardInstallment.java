package project.smartfinance.domain.card;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CardInstallment(int id, int purchaseId, int statementId, int number,
        BigDecimal amount, String status, String description, int categoryId,
        String categoryName, Integer entityId, String entityName,
        LocalDate statementDueDate, String statementReferenceMonth) {
}
