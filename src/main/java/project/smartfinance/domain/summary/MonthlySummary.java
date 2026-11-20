package project.smartfinance.domain.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlySummary(LocalDate month, BigDecimal realizedIncome,
        BigDecimal realizedExpenses, BigDecimal realizedResult,
        BigDecimal plannedIncome, BigDecimal plannedExpenses, BigDecimal pendingExpenses,
        BigDecimal projectedResult, BigDecimal reversalCreditsGenerated,
        BigDecimal reversalCreditsApplied, List<NecessityTotal> necessityTotals,
        List<CategoryTotal> categoryTotals) {
}
