package project.smartfinance.domain.wallet;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Wallet(int id, int ownerUserId, String name, WalletType type,
        String institutionName, BigDecimal initialBalance, LocalDate initialBalanceDate,
        boolean active, BigDecimal estimatedBalance) {

    public Wallet withEstimatedBalance(BigDecimal balance) {
        return new Wallet(id, ownerUserId, name, type, institutionName, initialBalance,
                initialBalanceDate, active, balance);
    }
}
