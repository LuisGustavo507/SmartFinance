package project.smartfinance.api.summary;

import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.summary.MonthlyPoints;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.summary.MonthlyPoint;

@RestController
@RequestMapping("/api/reports")
class MonthlyReportController {

    private final MonthlyPoints points;

    MonthlyReportController(MonthlyPoints points) {
        this.points = points;
    }

    @GetMapping("/monthly")
    List<MonthlyPoint> list(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth from,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth to,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return points.list(ownerId, from, to);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidPeriod() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
