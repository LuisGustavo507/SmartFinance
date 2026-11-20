package project.smartfinance.api.payable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.payable.Payables;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.domain.payable.Payable;
import project.smartfinance.domain.payable.PayablePayment;

@RestController
@RequestMapping("/api/payables")
class PayableController {
    private final Payables payables;

    PayableController(Payables payables) {
        this.payables = payables;
    }

    @PostMapping
    ResponseEntity<Payable> create(@Valid @RequestBody PayableRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payables.create(
                owner(authentication), request.categoryId(), request.entityId(),
                request.description(), request.necessity(), request.amount(), request.dueOn()));
    }

    @GetMapping
    List<Payable> list(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Authentication authentication) {
        return payables.list(owner(authentication), month);
    }

    @GetMapping("/{id}")
    Payable find(@PathVariable int id, Authentication authentication) {
        return payables.find(owner(authentication), id);
    }

    @PutMapping("/{id}")
    Payable update(@PathVariable int id, @Valid @RequestBody PayableRequest request,
            Authentication authentication) {
        return payables.update(owner(authentication), id, request.categoryId(),
                request.entityId(), request.description(), request.necessity(),
                request.amount(), request.dueOn());
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable int id, Authentication authentication) {
        payables.cancel(owner(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/payments")
    List<PayablePayment> payments(@PathVariable int id, Authentication authentication) {
        return payables.payments(owner(authentication), id);
    }

    @PostMapping("/{id}/payments")
    ResponseEntity<PayablePayment> pay(@PathVariable int id,
            @Valid @RequestBody PaymentRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(payables.pay(
                owner(authentication), id, request.walletId(), request.amount(), request.paidOn()));
    }

    @PostMapping("/{id}/payments/{paymentId}/reverse")
    PayablePayment reverse(@PathVariable int id, @PathVariable int paymentId,
            @Valid @RequestBody ReversalRequest request, Authentication authentication) {
        return payables.reverse(owner(authentication), id, paymentId, request.reason());
    }

    @ExceptionHandler(Payables.NotFound.class)
    ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Payables.Conflict.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalid() {
        return ResponseEntity.badRequest().build();
    }

    private static int owner(Authentication authentication) {
        return ((RegisteredUser) authentication.getPrincipal()).id();
    }

    record PayableRequest(@NotNull @Positive Integer categoryId, @Positive Integer entityId,
            @Size(max = 255) String description, @NotNull Necessity necessity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate dueOn) {
    }

    record PaymentRequest(@NotNull @Positive Integer walletId,
            @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate paidOn) {
    }

    record ReversalRequest(@NotNull @Size(min = 1, max = 255) String reason) {
    }
}
