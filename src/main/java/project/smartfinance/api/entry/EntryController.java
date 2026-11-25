package project.smartfinance.api.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
import project.smartfinance.application.entry.Entries;
import project.smartfinance.application.entry.EntryAlreadyProcessedException;
import project.smartfinance.application.entry.EntryReferenceNotFoundException;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.domain.entry.Entry;
import project.smartfinance.domain.expense.Necessity;

@RestController
@RequestMapping("/api/entries")
class EntryController {

    private final Entries entries;

    EntryController(Entries entries) {
        this.entries = entries;
    }

    @PostMapping
    ResponseEntity<Entry> create(@Valid @RequestBody CreateEntryRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.create(
                  ownerId, request.walletId(), request.categoryId(),
                  request.description(), request.amount(), request.occurredOn(),
                  request.entityId(), request.necessity(), request.newEntityName()));
    }

    @PostMapping("/planned")
    ResponseEntity<Entry> plan(@Valid @RequestBody PlanEntryRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.plan(
                  ownerId, request.walletId(), request.categoryId(),
                  request.description(), request.amount(), request.expectedOn(),
                  request.entityId(), request.necessity(), request.newEntityName()));
    }

    @PostMapping("/series")
    ResponseEntity<List<Entry>> createSeries(@Valid @RequestBody CreateSeriesRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return ResponseEntity.status(HttpStatus.CREATED).body(entries.createSeries(
                  ownerId, request.walletId(), request.categoryId(), request.description(),
                  request.amount(), request.firstExpectedOn(), request.totalOccurrences(),
                  request.entityId(), request.necessity(), request.newEntityName()));
    }

    @GetMapping("/series/{seriesId}")
    List<Entry> series(@PathVariable int seriesId, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return entries.series(ownerId, seriesId);
    }

    @PutMapping("/{id}")
    Entry update(@PathVariable int id, @Valid @RequestBody UpdateEntryRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return entries.update(ownerId, id, request.walletId(), request.categoryId(),
                 request.description(), request.amount(), request.expectedOn(), request.occurredOn(),
                 request.entityId(), request.necessity());
    }

    @PutMapping("/series/{seriesId}/occurrences/{occurrenceNumber}")
    List<Entry> updateSeriesFrom(@PathVariable int seriesId,
            @PathVariable int occurrenceNumber,
            @Valid @RequestBody UpdateEntryRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return entries.updateSeriesFrom(ownerId, seriesId, occurrenceNumber,
                 request.walletId(), request.categoryId(), request.description(),
                 request.amount(), request.expectedOn(), request.entityId(),
                 request.necessity());
    }

    @PutMapping("/{id}/entity")
    Entry updateEntity(@PathVariable int id, @Valid @RequestBody EntityLinkRequest request,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return entries.updateEntityLink(ownerId, id, request.entityId());
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(@PathVariable int id, Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        entries.cancel(ownerId, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    List<Entry> list(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) Integer entityId,
            Authentication authentication) {
        int ownerId = ((RegisteredUser) authentication.getPrincipal()).id();
        return entries.list(ownerId, month, entityId);
    }

    @ExceptionHandler(EntryReferenceNotFoundException.class)
    ResponseEntity<Void> missingReference() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(EntryAlreadyProcessedException.class)
    ResponseEntity<Void> alreadyProcessed() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidDate() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Void> duplicateEntity() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    record CreateEntryRequest(
            @NotNull @Positive Integer walletId,
            @NotNull @Positive Integer categoryId,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate occurredOn,
            @Positive Integer entityId,
            Necessity necessity,
            @Size(max = 120) String newEntityName) {
    }

    record PlanEntryRequest(
            @NotNull @Positive Integer walletId,
            @NotNull @Positive Integer categoryId,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate expectedOn,
            @Positive Integer entityId,
            Necessity necessity,
            @Size(max = 120) String newEntityName) {
    }

    record CreateSeriesRequest(
            @NotNull @Positive Integer walletId,
            @NotNull @Positive Integer categoryId,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @NotNull LocalDate firstExpectedOn,
            @NotNull @Min(1) @Max(120) Integer totalOccurrences,
            @Positive Integer entityId,
            Necessity necessity,
            @Size(max = 120) String newEntityName) {
    }

    record EntityLinkRequest(@Positive Integer entityId) {
    }

    record UpdateEntryRequest(
            @NotNull @Positive Integer walletId,
            @NotNull @Positive Integer categoryId,
            @Size(max = 255) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
            LocalDate expectedOn,
            LocalDate occurredOn,
            @Positive Integer entityId,
            Necessity necessity) {
    }
}
