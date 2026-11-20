package project.smartfinance.domain.summary;

import java.math.BigDecimal;
import project.smartfinance.domain.category.Category.Kind;

public record CategoryTotal(int categoryId, String categoryName, Kind kind,
        BigDecimal realizedIncome, BigDecimal realizedExpenses,
        BigDecimal plannedIncome, BigDecimal plannedExpenses, BigDecimal pendingExpenses) {
}
