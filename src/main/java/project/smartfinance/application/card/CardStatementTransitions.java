package project.smartfinance.application.card;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface CardStatementTransitions {

    /**
     * Applies the pending date-driven transitions: the cycle that started
     * becomes open, the cycle that ended becomes closed (or paid when it has no
     * charges) and an unpaid statement past its due date becomes overdue.
     */
    int regularize(LocalDate today, OffsetDateTime processedAt);
}
