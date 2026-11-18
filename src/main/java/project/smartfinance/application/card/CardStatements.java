package project.smartfinance.application.card;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.card.CardStatement;

@Service
public class CardStatements {

    private final CardPurchaseStore store;
    private final CardStatementTransitions transitions;
    private final Clock clock;

    public CardStatements(CardPurchaseStore store, CardStatementTransitions transitions,
            Clock clock) {
        this.store = store;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Transactional
    public List<CardStatement> list(int authenticatedUserId, YearMonth month) {
        transitions.regularize(LocalDate.now(clock), OffsetDateTime.now(clock));
        return store.listStatements(authenticatedUserId, month);
    }

    @Transactional
    public Optional<CardStatement> find(int id, int authenticatedUserId) {
        transitions.regularize(LocalDate.now(clock), OffsetDateTime.now(clock));
        return store.findStatement(authenticatedUserId, id);
    }
}
