package project.smartfinance.application.wallet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.wallet.Wallet;
import project.smartfinance.domain.wallet.WalletType;

@Service
public class Wallets {

    private final WalletStore store;
    private final WalletBalanceStore balances;

    public Wallets(WalletStore store, WalletBalanceStore balances) {
        this.store = store;
        this.balances = balances;
    }

    @Transactional
    public Wallet create(int authenticatedUserId, String name, WalletType type,
            String institutionName, BigDecimal initialBalance,
            LocalDate initialBalanceDate) {
        if (initialBalance.scale() > 2) {
            throw new IllegalArgumentException("O saldo inicial deve ter no máximo duas casas decimais.");
        }
        WalletType resolvedType = type == null ? WalletType.CARTEIRA_DINHEIRO : type;
        String trimmedName = name.strip();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Nome obrigatório.");
        }
        String institution = institutionName == null ? null : institutionName.strip();
        if (resolvedType == WalletType.CARTEIRA_DINHEIRO) {
            institution = null;
        } else if (institution == null || institution.isEmpty()) {
            throw new IllegalArgumentException("Informe a instituição da conta.");
        }
        return store.create(authenticatedUserId, trimmedName, resolvedType, institution,
                initialBalance, initialBalanceDate);
    }

    @Transactional(readOnly = true)
    public List<Wallet> list(int authenticatedUserId) {
        return store.listForOwner(authenticatedUserId).stream()
                .map(this::withBalance)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Wallet> find(int id, int authenticatedUserId) {
        return store.findForOwner(id, authenticatedUserId).map(this::withBalance);
    }

    private Wallet withBalance(Wallet wallet) {
        return wallet.withEstimatedBalance(balances.estimate(wallet.ownerUserId(), wallet.id()));
    }
}
