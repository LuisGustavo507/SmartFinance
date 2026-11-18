package project.smartfinance.api.card;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.card.CardPaymentNotAllowedException;
import project.smartfinance.application.card.CardPayments;
import project.smartfinance.application.card.CardReferenceNotFoundException;
import project.smartfinance.application.card.CardStatements;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.card.CardPayment;
import project.smartfinance.domain.card.CardStatement;

@RestController
@RequestMapping("/api/card-statements")
class CardStatementController {

    private final CardStatements statements;
    private final CardPayments payments;

    CardStatementController(CardStatements statements, CardPayments payments) {
        this.statements = statements;
        this.payments = payments;
    }

    @GetMapping
    List<CardStatement> list(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return statements.list(ownerId, month);
    }

    @GetMapping("/{id}")
    ResponseEntity<CardStatement> find(@PathVariable int id, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return statements.find(id, ownerId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/payments")
    ResponseEntity<CardPayment> pay(@PathVariable int id,
            @Valid @RequestBody PayStatementRequest request, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        CardPayment payment = payments.pay(ownerId, id, request.walletId(), request.amount(),
                request.paidOn());
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    @ExceptionHandler(CardReferenceNotFoundException.class)
    ResponseEntity<Void> missingReference() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(CardPaymentNotAllowedException.class)
    ResponseEntity<Void> notPayable() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.badRequest().build();
    }

    record PayStatementRequest(
            @NotNull @Positive Integer walletId,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull OffsetDateTime paidOn) {
    }
}
