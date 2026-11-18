package project.smartfinance.domain.card;

import java.time.LocalDate;
import java.time.YearMonth;

public record StatementPeriod(LocalDate closingDate, LocalDate dueDate,
        YearMonth referenceMonth, LocalDate cycleStart) {
}
