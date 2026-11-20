package project.smartfinance.domain.summary;

import java.math.BigDecimal;

public record EntityPeriodTotal(Integer entityId, String entityName,
        BigDecimal realizedIncome, BigDecimal realizedExpenses,
        BigDecimal plannedIncome, BigDecimal plannedExpenses, BigDecimal pendingExpenses) {
}
