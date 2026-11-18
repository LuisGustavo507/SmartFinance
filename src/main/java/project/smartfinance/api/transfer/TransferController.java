package project.smartfinance.api.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.transfer.TransferReferenceNotFoundException;
import project.smartfinance.application.transfer.Transfers;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.transfer.Transfer;

@RestController
@RequestMapping("/api/transfers")
class TransferController {

    private final Transfers transfers;

    TransferController(Transfers transfers) {
        this.transfers = transfers;
    }

    @PostMapping
    ResponseEntity<Transfer> create(@Valid @RequestBody CreateTransferRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(transfers.create(
                ownerId, request.sourceWalletId(), request.destinationWalletId(),
                request.amount(), request.description(), request.transferredAt()));
    }

    @GetMapping
    List<Transfer> list(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return transfers.list(ownerId, month);
    }

    @ExceptionHandler(TransferReferenceNotFoundException.class)
    ResponseEntity<Void> missingReference() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidTransfer() {
        return ResponseEntity.badRequest().build();
    }

    record CreateTransferRequest(
            @NotNull @Positive Integer sourceWalletId,
            @NotNull @Positive Integer destinationWalletId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 255) String description,
            @NotNull OffsetDateTime transferredAt) {
    }
}
