package project.smartfinance.application.summary;

import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.summary.EntityPeriodReport;

@Service
public class EntityPeriodReports {

    private final EntityPeriodReportReader reader;

    public EntityPeriodReports(EntityPeriodReportReader reader) {
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public EntityPeriodReport report(int authenticatedUserId, YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("O início do período deve vir antes do fim.");
        }
        return reader.report(authenticatedUserId, from.atDay(1), to.plusMonths(1).atDay(1).minusDays(1));
    }
}
