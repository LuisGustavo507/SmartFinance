package project.smartfinance.application.card;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.ReversalCredit;

@Service
public class CardReversals {

    private final CardReversalStore store;
    private final CardStatementTransitions transitions;
    private final Clock clock;

    public CardReversals(CardReversalStore store, CardStatementTransitions transitions, Clock clock) {
        this.store = store;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Transactional
    public CardPurchase reverse(int ownerUserId, int purchaseId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        transitions.regularize(LocalDate.now(clock), now);
        return store.reverse(ownerUserId, purchaseId, now)
                .orElseThrow(CardReferenceNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<ReversalCredit> credits(int ownerUserId, YearMonth month) {
        return store.credits(ownerUserId, month);
    }
}
