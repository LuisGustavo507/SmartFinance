package project.smartfinance.application.card;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardStatementProcessor {

    private final CardStatementTransitions transitions;
    private final Clock clock;

    public CardStatementProcessor(CardStatementTransitions transitions, Clock clock) {
        this.transitions = transitions;
        this.clock = clock;
    }

    // Catch up after downtime; the conditional updates make repeated runs harmless.
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "10 5 0 * * *", zone = "UTC")
    @Transactional
    public void processDue() {
        transitions.regularize(LocalDate.now(clock),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
