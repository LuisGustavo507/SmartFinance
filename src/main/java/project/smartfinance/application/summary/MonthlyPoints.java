package project.smartfinance.application.summary;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.summary.MonthlyPoint;

@Service
public class MonthlyPoints {

    private final MonthlyPointReader reader;

    public MonthlyPoints(MonthlyPointReader reader) {
        this.reader = reader;
    }

    /**
     * Every month of the period is returned, even the ones without movement.
     */
    @Transactional(readOnly = true)
    public List<MonthlyPoint> list(int authenticatedUserId, YearMonth from, YearMonth to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("O início do período deve vir antes do fim.");
        }
        Map<YearMonth, MonthlyPoint> found = reader
                .read(authenticatedUserId, from.atDay(1), to.plusMonths(1).atDay(1).minusDays(1))
                .stream()
                .collect(Collectors.toMap(point -> YearMonth.from(point.month()),
                        Function.identity()));
        List<MonthlyPoint> points = new ArrayList<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            points.add(found.getOrDefault(month,
                    MonthlyPoint.of(month.atDay(1), BigDecimal.ZERO, BigDecimal.ZERO,
                            BigDecimal.ZERO, BigDecimal.ZERO)));
        }
        return points;
    }
}
