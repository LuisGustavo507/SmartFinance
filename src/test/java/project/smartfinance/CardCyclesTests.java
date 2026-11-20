package project.smartfinance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import project.smartfinance.domain.card.CardCycles;
import project.smartfinance.domain.card.StatementPeriod;

class CardCyclesTests {

    @Test
    void closingDayStartsTheNextCycle() {
        StatementPeriod previous = CardCycles.periodFor(5, 14, LocalDate.of(2026, 11, 4));
        assertThat(previous.closingDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(previous.cycleStart()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(previous.dueDate()).isEqualTo(LocalDate.of(2026, 11, 14));

        for (int day : new int[] {5, 6}) {
            StatementPeriod next = CardCycles.periodFor(5, 14, LocalDate.of(2026, 11, day));
            assertThat(next.closingDate()).isEqualTo(LocalDate.of(2026, 12, 5));
            assertThat(next.cycleStart()).isEqualTo(LocalDate.of(2026, 11, 5));
            assertThat(next.dueDate()).isEqualTo(LocalDate.of(2026, 12, 14));
            assertThat(next.referenceMonth()).isEqualTo(YearMonth.of(2026, 12));
        }
    }

    @Test
    void installmentsAndYearChangeKeepTheSameExclusiveBoundary() {
        StatementPeriod first = CardCycles.periodFor(28, 5, LocalDate.of(2026, 11, 28));
        assertThat(first.closingDate()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(first.dueDate()).isEqualTo(LocalDate.of(2027, 1, 5));
        assertThat(first.referenceMonth()).isEqualTo(YearMonth.of(2027, 1));

        StatementPeriod second = CardCycles.periodFromClosing(28, 5,
                first.closingDate().plusMonths(1));
        assertThat(second.cycleStart()).isEqualTo(first.closingDate());
        assertThat(second.closingDate()).isEqualTo(LocalDate.of(2027, 1, 28));
        assertThat(second.dueDate()).isEqualTo(LocalDate.of(2027, 2, 5));
    }
}
