package project.smartfinance.api.card;

import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.card.CardReferenceNotFoundException;
import project.smartfinance.application.card.CardReversals;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.ReversalCredit;

@RestController
class CardReversalController {

    private final CardReversals reversals;

    CardReversalController(CardReversals reversals) {
        this.reversals = reversals;
    }

    @PostMapping("/api/card-purchases/{id}/reversal")
    CardPurchase reverse(@PathVariable int id, Authentication authentication) {
        return reversals.reverse(((RegisteredUser) authentication.getPrincipal()).id(), id);
    }

    @GetMapping("/api/card-credits")
    List<ReversalCredit> credits(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Authentication authentication) {
        return reversals.credits(((RegisteredUser) authentication.getPrincipal()).id(), month);
    }

    @ExceptionHandler(CardReferenceNotFoundException.class)
    ResponseEntity<Void> missing() {
        return ResponseEntity.notFound().build();
    }
}
