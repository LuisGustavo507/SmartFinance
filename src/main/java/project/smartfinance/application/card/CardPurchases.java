package project.smartfinance.application.card;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.category.CategoryStore;
import project.smartfinance.application.counterparty.CounterpartyStore;
import project.smartfinance.domain.card.CardCycles;
import project.smartfinance.domain.card.CardPurchase;
import project.smartfinance.domain.card.CreditCard;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.domain.card.StatementPeriod;
import project.smartfinance.domain.category.Category;

@Service
public class CardPurchases {

    private final CardPurchaseStore store;
    private final CreditCardStore cards;
    private final CategoryStore categories;
    private final CounterpartyStore counterparties;
    private final CardStatementTransitions transitions;
    private final Clock clock;

    public CardPurchases(CardPurchaseStore store, CreditCardStore cards,
            CategoryStore categories, CounterpartyStore counterparties,
            CardStatementTransitions transitions, Clock clock) {
        this.store = store;
        this.cards = cards;
        this.categories = categories;
        this.counterparties = counterparties;
        this.transitions = transitions;
        this.clock = clock;
    }

    @Transactional
    public CardPurchase register(int ownerUserId, int cardId, int categoryId,
            String description, BigDecimal amount, LocalDate purchasedOn,
            int installmentCount, Integer entityId, String necessity,
            String newEntityName) {
        CreditCard card = cards.findForOwner(cardId, ownerUserId)
                .orElseThrow(CardReferenceNotFoundException::new);        if (!card.active()) {
            throw new CardInactiveException();
        }
        Category category = categories.findForOwner(categoryId, ownerUserId)
                .orElseThrow(CardReferenceNotFoundException::new);
        if (!category.active() || category.kind() != Category.Kind.DESPESA) {
            throw new IllegalArgumentException("A compra exige uma categoria de despesa ativa.");
        }
        String entityName = null;
        Integer resolvedEntityId = entityId;
        if (newEntityName != null) {
            if (entityId != null || newEntityName.isBlank()) {
                throw new IllegalArgumentException(
                        "Escolha uma entidade ou informe um novo nome.");
            }
            var created = counterparties.create(ownerUserId, newEntityName.strip());
            resolvedEntityId = created.id();
            entityName = created.name();
        } else if (entityId != null) {
            var entity = counterparties.findForOwner(entityId, ownerUserId)
                    .orElseThrow(CardReferenceNotFoundException::new);
            if (!entity.active()) {
                throw new IllegalArgumentException("A entidade está desativada.");
            }
            entityName = entity.name();
        }
        Necessity value = Necessity.valueOf(necessity);
        long cents = validateAmount(amount);
        validateInstallments(installmentCount, cents);
        String trimmedDescription = description == null ? "" : description.strip();

        LocalDate today = LocalDate.now(clock);
        // The cycle boundary in UTC prevails over a stale status in the database.
        transitions.regularize(today, OffsetDateTime.now(clock));
        StatementPeriod first = CardCycles.periodFor(card.closingDay(), card.dueDay(),
                purchasedOn);
        if (first.closingDate().isBefore(today)) {
            throw new CardStatementClosedException();
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        int purchaseId = store.createPurchase(ownerUserId, cardId, categoryId, resolvedEntityId,
                trimmedDescription, amount, purchasedOn, installmentCount, value.name(), now);
        long base = cents / installmentCount;
        int remainder = (int) (cents % installmentCount);
        for (int number = 1; number <= installmentCount; number++) {
            StatementPeriod period = number == 1 ? first
                    : CardCycles.periodFromClosing(card.closingDay(), card.dueDay(),
                            first.closingDate().plusMonths(number - 1L));
            String status = period.cycleStart().isAfter(today) ? "PROJETADA" : "ABERTA";
            int statementId = store.findOrCreateStatement(ownerUserId, cardId, period, status, now);
            BigDecimal installmentAmount = BigDecimal.valueOf(base + (number <= remainder ? 1 : 0), 2);
            store.createInstallment(ownerUserId, purchaseId, statementId, number,
                    installmentAmount, now);
        }
        return store.findPurchase(ownerUserId, purchaseId);
    }

    /**
     * Corrects only the counterparty linked to a purchase, even after the
     * statement is closed. No value, date, category, installment or statement
     * changes.
     */
    @Transactional
    public CardPurchase updateEntityLink(int ownerUserId, int purchaseId, Integer entityId) {
        if (entityId != null) {
            boolean active = counterparties.findForOwner(entityId, ownerUserId)
                    .filter(entity -> entity.active())
                    .isPresent();
            if (!active) {
                throw new CardReferenceNotFoundException();
            }
        }
        if (!store.updateEntityLink(ownerUserId, purchaseId, entityId)) {
            throw new CardReferenceNotFoundException();
        }
        return store.findPurchase(ownerUserId, purchaseId);
    }

    private long validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("O valor deve ser maior que zero.");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("O valor deve ter no máximo duas casas decimais.");
        }
        return amount.movePointRight(2).longValueExact();
    }

    private void validateInstallments(int installmentCount, long cents) {
        if (installmentCount < 1) {
            throw new IllegalArgumentException("A quantidade de parcelas deve ser maior que zero.");
        }
        if (installmentCount > cents) {
            throw new IllegalArgumentException(
                    "A quantidade de parcelas não pode exceder o valor em centavos.");
        }
    }
}
