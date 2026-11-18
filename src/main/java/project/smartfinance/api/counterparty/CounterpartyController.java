package project.smartfinance.api.counterparty;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.counterparty.Counterparties;
import project.smartfinance.application.counterparty.CounterpartyNotFoundException;
import project.smartfinance.application.counterparty.CounterpartyInUseException;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.counterparty.Counterparty;

@RestController
@RequestMapping("/api/entities")
class CounterpartyController {

    private final Counterparties counterparties;

    CounterpartyController(Counterparties counterparties) {
        this.counterparties = counterparties;
    }

    @PostMapping
    ResponseEntity<Counterparty> create(@Valid @RequestBody CreateCounterpartyRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(counterparties.create(ownerId, request.name()));
    }

    @GetMapping
    List<Counterparty> list(Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return counterparties.list(ownerId);
    }

    @PatchMapping("/{id}/status")
    Counterparty setActive(@PathVariable int id, @Valid @RequestBody ChangeStatusRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return counterparties.setActive(ownerId, id, request.active());
    }

    @PutMapping("/{id}")
    Counterparty rename(@PathVariable int id, @Valid @RequestBody RenameRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return counterparties.rename(ownerId, id, request.name());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable int id, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        counterparties.delete(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(CounterpartyInUseException.class)
    ResponseEntity<Void> counterpartyInUse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(CounterpartyNotFoundException.class)
    ResponseEntity<Void> missingCounterparty() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidName() {
        return ResponseEntity.badRequest().build();
    }

    record CreateCounterpartyRequest(@NotBlank @Size(max = 120) String name) {
    }

    record ChangeStatusRequest(@NotNull Boolean active) {
    }

    record RenameRequest(@NotBlank @Size(max = 120) String name) {
    }
}
