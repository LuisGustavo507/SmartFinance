package project.smartfinance.application.wallet;

import java.math.BigDecimal;

public interface WalletBalanceStore {

    BigDecimal estimate(int ownerUserId, int walletId);
}
