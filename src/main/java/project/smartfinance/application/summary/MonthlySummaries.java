package project.smartfinance.application.summary;

import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.summary.MonthlySummary;

@Service
public class MonthlySummaries {

    private final MonthlySummaryReader reader;

    public MonthlySummaries(MonthlySummaryReader reader) {
        this.reader = reader;
    }

    @Transactional(readOnly = true)
    public MonthlySummary get(int authenticatedUserId, YearMonth month) {
        return reader.forOwnerAndMonth(authenticatedUserId, month.atDay(1));
    }

    @Transactional(readOnly = true)
    public MonthlySummary get(int authenticatedUserId, YearMonth month, Integer entityId) {
        return reader.forOwnerAndMonth(authenticatedUserId, month.atDay(1), entityId);
    }
}
