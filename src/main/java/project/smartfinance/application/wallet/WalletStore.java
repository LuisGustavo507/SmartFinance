package project.smartfinance.application.wallet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.wallet.Wallet;
import project.smartfinance.domain.wallet.WalletType;

public interface WalletStore {

    Wallet create(int ownerUserId, String name, WalletType type, String institutionName,
            BigDecimal initialBalance, LocalDate initialBalanceDate);

    List<Wallet> listForOwner(int ownerUserId);

    Optional<Wallet> findForOwner(int id, int ownerUserId);
}
