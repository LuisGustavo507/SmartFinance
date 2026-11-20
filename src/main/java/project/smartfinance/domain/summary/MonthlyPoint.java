package project.smartfinance.domain.summary;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyPoint(LocalDate month, BigDecimal realizedIncome,
        BigDecimal realizedExpenses, BigDecimal plannedIncome, BigDecimal plannedExpenses,
        BigDecimal pendingExpenses,
        BigDecimal realizedResult, BigDecimal projectedResult) {

    public static MonthlyPoint of(LocalDate month, BigDecimal realizedIncome,
            BigDecimal realizedExpenses, BigDecimal plannedIncome,
            BigDecimal plannedExpenses) {
        return of(month, realizedIncome, realizedExpenses, plannedIncome,
                plannedExpenses, BigDecimal.ZERO);
    }

    public static MonthlyPoint of(LocalDate month, BigDecimal realizedIncome,
            BigDecimal realizedExpenses, BigDecimal plannedIncome,
            BigDecimal plannedExpenses, BigDecimal pendingExpenses) {
        return new MonthlyPoint(month, realizedIncome, realizedExpenses, plannedIncome,
                plannedExpenses, pendingExpenses, realizedIncome.subtract(realizedExpenses),
                realizedIncome.add(plannedIncome)
                        .subtract(realizedExpenses).subtract(plannedExpenses)
                        .subtract(pendingExpenses));
    }
}
