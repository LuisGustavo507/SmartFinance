package project.smartfinance.domain.summary;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MonthlyPoint(LocalDate month, BigDecimal realizedIncome,
        BigDecimal realizedExpenses, BigDecimal plannedIncome, BigDecimal plannedExpenses,
        BigDecimal realizedResult, BigDecimal projectedResult) {

    public static MonthlyPoint of(LocalDate month, BigDecimal realizedIncome,
            BigDecimal realizedExpenses, BigDecimal plannedIncome,
            BigDecimal plannedExpenses) {
        return new MonthlyPoint(month, realizedIncome, realizedExpenses, plannedIncome,
                plannedExpenses, realizedIncome.subtract(realizedExpenses),
                realizedIncome.add(plannedIncome)
                        .subtract(realizedExpenses).subtract(plannedExpenses));
    }
}
