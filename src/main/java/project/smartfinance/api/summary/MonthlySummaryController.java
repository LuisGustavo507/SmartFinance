package project.smartfinance.api.summary;

import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.summary.MonthlySummaries;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.summary.MonthlySummary;

@RestController
@RequestMapping("/api/monthly-summary")
class MonthlySummaryController {

    private final MonthlySummaries summaries;

    MonthlySummaryController(MonthlySummaries summaries) {
        this.summaries = summaries;
    }

    @GetMapping
    MonthlySummary get(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Integer entityId,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return summaries.get(ownerId, month, entityId);
    }
}
