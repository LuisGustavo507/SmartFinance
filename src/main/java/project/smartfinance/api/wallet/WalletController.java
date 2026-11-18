package project.smartfinance.api.wallet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.application.wallet.Wallets;
import project.smartfinance.domain.wallet.Wallet;
import project.smartfinance.domain.wallet.WalletType;

@RestController
@RequestMapping("/api/wallets")
class WalletController {

    private final Wallets wallets;

    WalletController(Wallets wallets) {
        this.wallets = wallets;
    }

    @PostMapping
    ResponseEntity<Wallet> create(@Valid @RequestBody CreateWalletRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(wallets.create(ownerId,
                request.name(), request.type(), request.institutionName(),
                request.initialBalance(), request.initialBalanceDate()));
    }

    @GetMapping
    List<Wallet> list(Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return wallets.list(ownerId);
    }

    @GetMapping("/{id}")
    ResponseEntity<Wallet> find(@PathVariable int id, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return wallets.find(id, ownerId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidAmount() {
        return ResponseEntity.badRequest().build();
    }

    record CreateWalletRequest(
            @NotBlank @Size(max = 120) String name,
            WalletType type,
            @Size(max = 120) String institutionName,
            @NotNull @Digits(integer = 17, fraction = 2) BigDecimal initialBalance,
            @NotNull LocalDate initialBalanceDate) {
    }
}
