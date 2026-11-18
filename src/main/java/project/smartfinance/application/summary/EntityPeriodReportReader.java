package project.smartfinance.application.summary;

import java.time.LocalDate;
import project.smartfinance.domain.summary.EntityPeriodReport;

public interface EntityPeriodReportReader {

    EntityPeriodReport report(int ownerUserId, LocalDate from, LocalDate to);
}
