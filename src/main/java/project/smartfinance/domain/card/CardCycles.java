package project.smartfinance.domain.card;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Cycles of a credit card statement.
 *
 * <p>The closing day starts the next cycle. A purchase on that day belongs to
 * the statement closing the following month; a purchase on the previous day
 * belongs to the statement closing that day. When the due day is not greater
 * than the closing day, the due date falls in the month after the closing date.
 */
public final class CardCycles {

    private CardCycles() {
    }

    public static StatementPeriod periodFor(int closingDay, int dueDay, LocalDate date) {
        LocalDate closing = closingAfter(closingDay, date);
        return periodFromClosing(closingDay, dueDay, closing);
    }

    public static StatementPeriod periodFromClosing(int closingDay, int dueDay,
            LocalDate closing) {
        LocalDate due = dueDay <= closingDay
                ? closing.plusMonths(1).withDayOfMonth(dueDay)
                : closing.withDayOfMonth(dueDay);
        return new StatementPeriod(closing, due, YearMonth.from(due),
                closing.minusMonths(1));
    }

    private static LocalDate closingAfter(int closingDay, LocalDate date) {
        LocalDate candidate = date.withDayOfMonth(1).withDayOfMonth(closingDay);
        if (!candidate.isAfter(date)) {
            return candidate.plusMonths(1);
        }
        return candidate;
    }
}
