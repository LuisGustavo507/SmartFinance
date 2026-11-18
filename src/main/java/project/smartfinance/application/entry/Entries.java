package project.smartfinance.application.entry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.category.CategoryStore;
import project.smartfinance.application.counterparty.CounterpartyStore;
import project.smartfinance.application.wallet.WalletStore;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.counterparty.Counterparty;
import project.smartfinance.domain.entry.Entry;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.domain.wallet.Wallet;

@Service
public class Entries {

    private static String normalizeDescription(String description) {
        return description == null ? "" : description.strip();
    }

    private final EntryStore store;
    private final CategoryStore categories;
    private final WalletStore wallets;
    private final CounterpartyStore counterparties;
    private final PlannedEntryTransitions transitions;
    private final Clock clock;

    public Entries(EntryStore store, CategoryStore categories, WalletStore wallets,
            CounterpartyStore counterparties, PlannedEntryTransitions transitions,
            Clock clock) {
        this.store = store;
        this.categories = categories;
        this.wallets = wallets;
        this.counterparties = counterparties;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Transactional
    public Entry create(int ownerId, int walletId, int categoryId, String description,
            BigDecimal amount, LocalDate occurredOn, Integer entityId, Necessity necessity,
            String newEntityName) {
        validateEntityChoice(entityId, newEntityName);
        References refs = validateReferences(ownerId, walletId, categoryId,
                entityId, amount, occurredOn);
        if (occurredOn.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A data deve estar entre o início da carteira e hoje.");
        }
        Counterparty entity = entityForCreation(ownerId, entityId, newEntityName,
                refs.counterparty());
        return store.create(ownerId, walletId, categoryId, refs.category().kind(),
                normalizeDescription(description), amount, occurredOn,
                now(), entity == null ? null : entity.id(),
                entity == null ? null : entity.name(),
                necessityForNew(refs.category().kind(), necessity));
    }

    @Transactional
    public Entry plan(int ownerId, int walletId, int categoryId, String description,
            BigDecimal amount, LocalDate expectedOn, Integer entityId, Necessity necessity,
            String newEntityName) {
        validateEntityChoice(entityId, newEntityName);
        References refs = validateReferences(ownerId, walletId, categoryId,
                entityId, amount, expectedOn);
        if (!expectedOn.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A data prevista deve ser futura.");
        }
        Counterparty entity = entityForCreation(ownerId, entityId, newEntityName,
                refs.counterparty());
        return store.createPlanned(ownerId, walletId, categoryId, refs.category().kind(),
                normalizeDescription(description), amount, expectedOn, now(),
                entity == null ? null : entity.id(),
                entity == null ? null : entity.name(),
                necessityForNew(refs.category().kind(), necessity));
    }

    @Transactional
    public List<Entry> createSeries(int ownerId, int walletId, int categoryId,
            String description, BigDecimal amount, LocalDate firstExpectedOn,
            int totalOccurrences, Integer entityId, Necessity necessity,
            String newEntityName) {
        validateEntityChoice(entityId, newEntityName);
        if (totalOccurrences < 1 || totalOccurrences > 120
                || !firstExpectedOn.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A série deve ser finita e começar no futuro.");
        }
        References refs = validateReferences(ownerId, walletId, categoryId,
                entityId, amount, firstExpectedOn);
        YearMonth firstMonth = YearMonth.from(firstExpectedOn);
        try {
            if (firstMonth.plusMonths(totalOccurrences - 1).getYear() > 9999) {
                throw new IllegalArgumentException("A série ultrapassa o ano 9999.");
            }
        } catch (DateTimeException invalidEnd) {
            throw new IllegalArgumentException("A série ultrapassa o intervalo de datas.", invalidEnd);
        }
        int dayOfMonth = firstExpectedOn.getDayOfMonth();
        List<LocalDate> dates = java.util.stream.IntStream.range(0, totalOccurrences)
                .mapToObj(index -> firstMonth.plusMonths(index))
                .map(month -> month.atDay(Math.min(dayOfMonth, month.lengthOfMonth())))
                .toList();
        Counterparty entity = entityForCreation(ownerId, entityId, newEntityName,
                refs.counterparty());
        return store.createSeries(ownerId, walletId, categoryId, refs.category().kind(),
                normalizeDescription(description), amount, dates, now(),
                entity == null ? null : entity.id(),
                entity == null ? null : entity.name(),
                necessityForNew(refs.category().kind(), necessity));
    }

    @Transactional(readOnly = true)
    public List<Entry> series(int ownerId, int seriesId) {
        List<Entry> occurrences = store.listForOwnerAndSeries(ownerId, seriesId);
        if (occurrences.isEmpty()) throw new EntryReferenceNotFoundException();
        return occurrences;
    }

    @Transactional
    public Entry update(int ownerId, int id, int walletId, int categoryId,
            String description, BigDecimal amount, LocalDate expectedOn, Integer entityId,
            Necessity necessity) {
        Entry current = store.findForOwner(ownerId, id)
                .orElseThrow(EntryReferenceNotFoundException::new);
        if (!"PREVISTO".equals(current.status())) {
            throw new EntryAlreadyProcessedException();
        }
        References refs = validateReferences(ownerId, walletId, categoryId, entityId,
                amount, expectedOn);
        requireFuture(expectedOn);
        return store.updatePlanned(ownerId, id, walletId, categoryId,
                refs.category().kind(), normalizeDescription(description), amount, expectedOn, entityId,
                refs.counterparty() == null ? null : refs.counterparty().name(),
                necessityForEdit(refs.category().kind(), current, necessity));
    }

    /**
     * Edits one occurrence and every later editable occurrence of the same series.
     * Effected and cancelled occurrences keep their original data.
     */
    @Transactional
    public List<Entry> updateSeriesFrom(int ownerId, int seriesId, int occurrenceNumber,
            int walletId, int categoryId, String description, BigDecimal amount,
            LocalDate expectedOn, Integer entityId, Necessity necessity) {
        if (occurrenceNumber < 1) {
            throw new IllegalArgumentException("A ocorrência deve começar em 1.");
        }
        List<Entry> occurrences = store.listForOwnerAndSeries(ownerId, seriesId);
        if (occurrences.isEmpty()) {
            throw new EntryReferenceNotFoundException();
        }
        Entry anchor = occurrences.stream()
                .filter(occurrence -> occurrence.occurrenceNumber() != null
                        && occurrence.occurrenceNumber() == occurrenceNumber)
                .findFirst()
                .orElseThrow(EntryReferenceNotFoundException::new);
        if (!"PREVISTO".equals(anchor.status())) {
            throw new EntryAlreadyProcessedException();
        }
        References refs = validateReferences(ownerId, walletId, categoryId, entityId,
                amount, expectedOn);
        requireFuture(expectedOn);
        YearMonth anchorMonth = YearMonth.from(expectedOn);
        int dayOfMonth = expectedOn.getDayOfMonth();
        List<Entry> updated = new ArrayList<>();
        for (Entry occurrence : occurrences) {
            if (occurrence.occurrenceNumber() == null
                    || occurrence.occurrenceNumber() < occurrenceNumber
                    || !"PREVISTO".equals(occurrence.status())) {
                continue;
            }
            LocalDate date;
            try {
                YearMonth month = anchorMonth.plusMonths(
                        occurrence.occurrenceNumber() - occurrenceNumber);
                date = month.atDay(Math.min(dayOfMonth, month.lengthOfMonth()));
            } catch (DateTimeException overflow) {
                throw new IllegalArgumentException(
                        "A série ultrapassa o intervalo de datas.", overflow);
            }
            updated.add(store.updatePlanned(ownerId, occurrence.id(), walletId, categoryId,
                    refs.category().kind(), normalizeDescription(description), amount, date, entityId,
                    refs.counterparty() == null ? null : refs.counterparty().name(),
                    necessityForEdit(refs.category().kind(), occurrence, necessity)));
        }
        return updated;
    }

    /**
     * Corrects only the counterparty linked to a record, even after it is
     * effected. No value, date, account, category or status changes.
     */
    @Transactional
    public Entry updateEntityLink(int ownerId, int id, Integer entityId) {
        if (store.findForOwner(ownerId, id).isEmpty()) {
            throw new EntryReferenceNotFoundException();
        }
        requireActiveEntity(ownerId, entityId);
        return store.updateEntityLink(ownerId, id, entityId)
                .orElseThrow(EntryReferenceNotFoundException::new);
    }

    private void requireActiveEntity(int ownerId, Integer entityId) {
        if (entityId == null) {
            return;
        }
        boolean active = counterparties.findForOwner(entityId, ownerId)
                .filter(Counterparty::active)
                .isPresent();
        if (!active) {
            throw new EntryReferenceNotFoundException();
        }
    }

    @Transactional
    public void cancel(int ownerId, int id) {
        if (transitions.cancel(id, ownerId)) {
            return;
        }
        if (transitions.existsForOwner(id, ownerId)) {
            throw new EntryAlreadyProcessedException();
        }
        throw new EntryReferenceNotFoundException();
    }

    @Transactional(readOnly = true)
    public List<Entry> list(int ownerId, YearMonth month, Integer entityId) {
        return store.listForOwnerAndMonth(ownerId, month.atDay(1), entityId);
    }

    private void requireFuture(LocalDate expectedOn) {
        if (!expectedOn.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("A data prevista deve ser futura.");
        }
    }

    private Counterparty entityForCreation(int ownerId, Integer entityId,
            String newEntityName, Counterparty existing) {
        if (newEntityName == null) {
            return existing;
        }
        validateEntityChoice(entityId, newEntityName);
        return counterparties.create(ownerId, newEntityName.strip());
    }

    private void validateEntityChoice(Integer entityId, String newEntityName) {
        if (newEntityName == null) {
            return;
        }
        if (entityId != null || newEntityName.isBlank()) {
            throw new IllegalArgumentException("Escolha uma entidade ou informe um novo nome.");
        }
    }

    private Necessity necessityForNew(Category.Kind kind, Necessity necessity) {
        if (kind == Category.Kind.DESPESA && necessity == null) {
            throw new IllegalArgumentException("Classifique a necessidade da despesa.");
        }
        if (kind == Category.Kind.RECEITA && necessity != null) {
            throw new IllegalArgumentException("Receitas não possuem necessidade.");
        }
        return necessity;
    }

    private Necessity necessityForEdit(Category.Kind kind, Entry current, Necessity requested) {
        if (kind == Category.Kind.RECEITA) {
            if (requested != null) {
                throw new IllegalArgumentException("Receitas não possuem necessidade.");
            }
            return null;
        }
        if (requested != null) {
            return requested;
        }
        if (current.kind() == Category.Kind.RECEITA) {
            throw new IllegalArgumentException("Classifique a necessidade da despesa.");
        }
        return current.necessity();
    }

    private References validateReferences(int ownerId, int walletId, int categoryId,
            Integer entityId, BigDecimal amount, LocalDate date) {
        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("O valor deve ser positivo e ter até duas casas decimais.");
        }
        Wallet wallet = wallets.findForOwner(walletId, ownerId)
                .filter(Wallet::active)
                .orElseThrow(EntryReferenceNotFoundException::new);
        Category category = categories.findForOwner(categoryId, ownerId)
                .filter(Category::active)
                .orElseThrow(EntryReferenceNotFoundException::new);
        if (date.isBefore(wallet.initialBalanceDate())) {
            throw new IllegalArgumentException("A data antecede o saldo inicial da carteira.");
        }
        Counterparty counterparty = entityId == null ? null :
                counterparties.findForOwner(entityId, ownerId)
                        .filter(Counterparty::active)
                        .orElseThrow(EntryReferenceNotFoundException::new);
        return new References(category, counterparty);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private record References(Category category, Counterparty counterparty) {
    }
}
