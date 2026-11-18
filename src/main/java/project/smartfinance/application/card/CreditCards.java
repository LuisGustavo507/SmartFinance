package project.smartfinance.application.card;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.wallet.WalletStore;
import project.smartfinance.domain.card.CreditCard;

@Service
public class CreditCards {

    private final CreditCardStore store;
    private final WalletStore wallets;

    public CreditCards(CreditCardStore store, WalletStore wallets) {
        this.store = store;
        this.wallets = wallets;
    }

    @Transactional
    public CreditCard create(int authenticatedUserId, String name, String institutionName,
            int closingDay, int dueDay, BigDecimal creditLimit,
            Integer defaultPaymentWalletId) {
        validate(authenticatedUserId, closingDay, dueDay, creditLimit, defaultPaymentWalletId);
        return store.create(authenticatedUserId, normalized(name),
                normalized(institutionName), closingDay, dueDay, creditLimit,
                defaultPaymentWalletId);
    }

    @Transactional
    public CreditCard update(int id, int authenticatedUserId, String name, String institutionName,
            int closingDay, int dueDay, BigDecimal creditLimit, Integer defaultPaymentWalletId) {
        validate(authenticatedUserId, closingDay, dueDay, creditLimit, defaultPaymentWalletId);
        return store.update(id, authenticatedUserId, normalized(name),
                normalized(institutionName), closingDay, dueDay, creditLimit,
                defaultPaymentWalletId).orElseThrow(CardReferenceNotFoundException::new);
    }

    @Transactional
    public CreditCard setActive(int id, int authenticatedUserId, boolean active) {
        return store.setActive(id, authenticatedUserId, active)
                .orElseThrow(CardReferenceNotFoundException::new);
    }

    private void validate(int authenticatedUserId, int closingDay, int dueDay,
            BigDecimal creditLimit, Integer defaultPaymentWalletId) {
        if (closingDay < 1 || closingDay > 28) {
            throw new IllegalArgumentException("O dia de fechamento deve ficar entre 1 e 28.");
        }
        if (dueDay < 1 || dueDay > 28) {
            throw new IllegalArgumentException("O dia de vencimento deve ficar entre 1 e 28.");
        }
        if (creditLimit != null && creditLimit.signum() <= 0) {
            throw new IllegalArgumentException("O limite deve ser maior que zero.");
        }
        if (defaultPaymentWalletId != null
                && wallets.findForOwner(defaultPaymentWalletId, authenticatedUserId).isEmpty()) {
            throw new CardReferenceNotFoundException();
        }
    }

    @Transactional(readOnly = true)
    public List<CreditCard> list(int authenticatedUserId) {
        return store.listForOwner(authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public Optional<CreditCard> find(int id, int authenticatedUserId) {
        return store.findForOwner(id, authenticatedUserId);
    }

    private String normalized(String value) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Campo obrigatório.");
        }
        return trimmed;
    }
}
