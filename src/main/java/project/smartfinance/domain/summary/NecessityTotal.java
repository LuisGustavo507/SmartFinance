package project.smartfinance.domain.summary;

import java.math.BigDecimal;
import project.smartfinance.domain.expense.Necessity;

public record NecessityTotal(Necessity necessity, BigDecimal realizedExpenses,
        BigDecimal plannedExpenses, BigDecimal pendingExpenses) {
}
