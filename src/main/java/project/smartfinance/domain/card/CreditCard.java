package project.smartfinance.domain.card;

import java.math.BigDecimal;

public record CreditCard(int id, int ownerUserId, String name, String institutionName,
        int closingDay, int dueDay, BigDecimal creditLimit,
        Integer defaultPaymentWalletId, boolean active) {
}
