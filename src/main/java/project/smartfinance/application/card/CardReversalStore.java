package project.smartfinance.application.card;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.ReversalCredit;

public interface CardReversalStore {

    Optional<CardPurchase> reverse(int ownerUserId, int purchaseId, OffsetDateTime now);

    List<ReversalCredit> credits(int ownerUserId, YearMonth month);
}
