package project.smartfinance.api.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.card.CardInactiveException;
import project.smartfinance.application.card.CardPurchases;
import project.smartfinance.application.card.CardReferenceNotFoundException;
import project.smartfinance.application.card.CardStatementClosedException;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.expense.Necessity;

@RestController
@RequestMapping("/api/card-purchases")
class CardPurchaseController {

    private final CardPurchases purchases;

    CardPurchaseController(CardPurchases purchases) {
        this.purchases = purchases;
    }

    @PostMapping
    ResponseEntity<CardPurchase> create(@Valid @RequestBody CreateCardPurchaseRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(purchases.register(ownerId,
                request.cardId(), request.categoryId(), request.description(), request.amount(),
                request.purchasedOn(), request.installments(), request.entityId(),
                request.necessity().name(), request.newEntityName()));
    }

    @PutMapping("/{id}/entity")
    CardPurchase updateEntity(@PathVariable int id, @Valid @RequestBody EntityLinkRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return purchases.updateEntityLink(ownerId, id, request.entityId());
    }

    @ExceptionHandler(CardReferenceNotFoundException.class)
    ResponseEntity<Void> missingReference() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler({CardStatementClosedException.class, CardInactiveException.class})
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Void> duplicateEntity() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    record EntityLinkRequest(@Positive Integer entityId) {
    }

    record CreateCardPurchaseRequest(
            @NotNull @Positive Integer cardId,
            @NotNull @Positive Integer categoryId,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate purchasedOn,
            @NotNull @Min(1) @Max(120) Integer installments,
            @Positive Integer entityId,
            @NotNull Necessity necessity,
            @Size(max = 120) String newEntityName) {
    }
}
