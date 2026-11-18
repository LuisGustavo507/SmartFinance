package project.smartfinance.api.card;

import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.card.CardReferenceNotFoundException;
import project.smartfinance.application.card.CardCycleLockedException;
import project.smartfinance.application.card.CreditCards;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.card.CreditCard;

@RestController
@RequestMapping("/api/cards")
class CardController {

    private final CreditCards cards;

    CardController(CreditCards cards) {
        this.cards = cards;
    }

    @PostMapping
    ResponseEntity<CreditCard> create(@Valid @RequestBody CreateCardRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(cards.create(ownerId,
                request.name(), request.institutionName(), request.closingDay(),
                request.dueDay(), request.creditLimit(), request.defaultPaymentWalletId()));
    }

    @GetMapping
    List<CreditCard> list(Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return cards.list(ownerId);
    }

    @GetMapping("/{id}")
    ResponseEntity<CreditCard> find(@PathVariable int id, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return cards.find(id, ownerId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    CreditCard update(@PathVariable int id, @Valid @RequestBody CreateCardRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return cards.update(id, ownerId, request.name(), request.institutionName(),
                request.closingDay(), request.dueDay(), request.creditLimit(),
                request.defaultPaymentWalletId());
    }

    @PatchMapping("/{id}/status")
    CreditCard setActive(@PathVariable int id, @Valid @RequestBody CardStatusRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return cards.setActive(id, ownerId, request.active());
    }

    @ExceptionHandler(CardReferenceNotFoundException.class)
    ResponseEntity<Void> missingReference() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(CardCycleLockedException.class)
    ResponseEntity<Void> lockedCycle() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.badRequest().build();
    }

    record CreateCardRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String institutionName,
            @NotNull @Min(1) @Max(28) Integer closingDay,
            @NotNull @Min(1) @Max(28) Integer dueDay,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal creditLimit,
            @Positive Integer defaultPaymentWalletId) {
    }

    record CardStatusRequest(@NotNull Boolean active) {
    }
}
