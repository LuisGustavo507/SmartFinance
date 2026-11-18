package project.smartfinance.domain.summary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record EntityPeriodReport(LocalDate from, LocalDate to, List<EntityPeriodTotal> entities) {

    public BigDecimal realizedIncome() {
        return entities.stream()
                .map(EntityPeriodTotal::realizedIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal realizedExpenses() {
        return entities.stream()
                .map(EntityPeriodTotal::realizedExpenses)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal plannedIncome() {
        return entities.stream()
                .map(EntityPeriodTotal::plannedIncome)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal plannedExpenses() {
        return entities.stream()
                .map(EntityPeriodTotal::plannedExpenses)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
