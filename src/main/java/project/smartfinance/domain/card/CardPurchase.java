package project.smartfinance.domain.card;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CardPurchase(int id, int ownerUserId, int cardId, int categoryId,
        Integer entityId, String description, BigDecimal totalAmount,
        LocalDate purchasedOn, int installmentCount, String necessity, String status,
        List<CardInstallment> installments) {
}
