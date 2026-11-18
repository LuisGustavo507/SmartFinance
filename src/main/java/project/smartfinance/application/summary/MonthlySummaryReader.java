package project.smartfinance.application.summary;

import java.time.LocalDate;
import project.smartfinance.domain.summary.MonthlySummary;

public interface MonthlySummaryReader {

    MonthlySummary forOwnerAndMonth(int ownerUserId, LocalDate month);

    MonthlySummary forOwnerAndMonth(int ownerUserId, LocalDate month, Integer entityId);
}
