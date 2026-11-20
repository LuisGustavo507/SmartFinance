package project.smartfinance.application.payable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.category.CategoryStore;
import project.smartfinance.application.counterparty.CounterpartyStore;
import project.smartfinance.application.wallet.WalletStore;
import project.smartfinance.domain.category.Category;
import project.smartfinance.domain.counterparty.Counterparty;
import project.smartfinance.domain.expense.Necessity;
import project.smartfinance.domain.payable.Payable;
import project.smartfinance.domain.payable.PayablePayment;
import project.smartfinance.domain.wallet.Wallet;

@Service
public class Payables {
    private final PayableStore store;
    private final CategoryStore categories;
    private final CounterpartyStore counterparties;
    private final WalletStore wallets;
    private final Clock clock;

    public Payables(PayableStore store, CategoryStore categories,
            CounterpartyStore counterparties, WalletStore wallets, Clock clock) {
        this.store = store;
        this.categories = categories;
        this.counterparties = counterparties;
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    public Payable create(int ownerId, int categoryId, Integer entityId, String description,
            Necessity necessity, BigDecimal amount, LocalDate dueOn) {
        validate(ownerId, categoryId, entityId, description, necessity, amount, dueOn);
        return store.create(ownerId, categoryId, entityId, normalize(description),
                necessity.name(), amount, dueOn, now(), today());
    }

    @Transactional(readOnly = true)
    public List<Payable> list(int ownerId, YearMonth month) {
        return store.list(ownerId, month.atDay(1), today());
    }

    @Transactional(readOnly = true)
    public Payable find(int ownerId, int id) {
        return store.find(ownerId, id, today()).orElseThrow(NotFound::new);
    }

    @Transactional(readOnly = true)
    public List<PayablePayment> payments(int ownerId, int id) {
        find(ownerId, id);
        return store.payments(ownerId, id);
    }

    @Transactional
    public Payable update(int ownerId, int id, int categoryId, Integer entityId,
            String description, Necessity necessity, BigDecimal amount, LocalDate dueOn) {
        store.lock(ownerId, id);
        Payable current = find(ownerId, id);
        if ("CANCELADA".equals(current.status()) || "QUITADA".equals(current.status())) {
            throw new Conflict();
        }
        validate(ownerId, categoryId, entityId, description, necessity, amount, dueOn);
        return store.update(ownerId, id, categoryId, entityId, normalize(description),
                necessity.name(), amount, dueOn, today());
    }

    @Transactional
    public void cancel(int ownerId, int id) {
        store.lock(ownerId, id);
        Payable current = find(ownerId, id);
        if ("CANCELADA".equals(current.status()) || "QUITADA".equals(current.status())) {
            throw new Conflict();
        }
        store.cancel(ownerId, id, now());
    }

    @Transactional
    public PayablePayment pay(int ownerId, int id, int walletId, BigDecimal requestedAmount,
            LocalDate paidOn) {
        store.lock(ownerId, id);
        Payable current = find(ownerId, id);
        if ("CANCELADA".equals(current.status()) || "QUITADA".equals(current.status())) {
            throw new Conflict();
        }
        if (requestedAmount != null && requestedAmount.compareTo(current.amount()) != 0) {
            throw new IllegalArgumentException("O pagamento deve ser integral.");
        }
        Wallet wallet = wallets.findForOwner(walletId, ownerId)
                .filter(Wallet::active).orElseThrow(NotFound::new);
        if (paidOn.isAfter(today()) || paidOn.isBefore(wallet.initialBalanceDate())) {
            throw new IllegalArgumentException("A data do pagamento deve estar entre o início da conta e hoje.");
        }
        return store.pay(ownerId, id, walletId, current.amount(), paidOn, now());
    }

    @Transactional
    public PayablePayment reverse(int ownerId, int id, int paymentId, String reason) {
        store.lock(ownerId, id);
        Payable current = find(ownerId, id);
        PayablePayment payment = store.payment(ownerId, id, paymentId).orElseThrow(NotFound::new);
        if ("CANCELADA".equals(current.status()) || payment.reversedAt() != null) {
            throw new Conflict();
        }
        if (reason == null || reason.isBlank() || reason.strip().length() > 255) {
            throw new IllegalArgumentException("Informe o motivo do estorno (até 255 caracteres).");
        }
        store.reverse(ownerId, paymentId, now(), reason.strip());
        return store.payment(ownerId, id, paymentId).orElseThrow(NotFound::new);
    }

    private void validate(int ownerId, int categoryId, Integer entityId,
            String description, Necessity necessity, BigDecimal amount, LocalDate dueOn) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 17 || dueOn == null
                || necessity == null || normalize(description).length() > 255) {
            throw new IllegalArgumentException("Confira valor, vencimento, necessidade e descrição.");
        }
        categories.findForOwner(categoryId, ownerId)
                .filter(Category::active)
                .filter(category -> category.kind() == Category.Kind.DESPESA)
                .orElseThrow(NotFound::new);
        if (entityId != null) {
            counterparties.findForOwner(entityId, ownerId)
                    .filter(Counterparty::active).orElseThrow(NotFound::new);
        }
    }

    private String normalize(String description) {
        return description == null ? "" : description.strip();
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    public static class NotFound extends RuntimeException {
    }

    public static class Conflict extends RuntimeException {
    }
}
