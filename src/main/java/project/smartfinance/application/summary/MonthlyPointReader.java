package project.smartfinance.application.summary;

import java.time.LocalDate;
import java.util.List;
import project.smartfinance.domain.summary.MonthlyPoint;

public interface MonthlyPointReader {

    List<MonthlyPoint> read(int ownerUserId, LocalDate from, LocalDate to);
}
