package project.smartfinance.application.card;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.card.CreditCard;

public interface CreditCardStore {

    CreditCard create(int ownerUserId, String name, String institutionName, int closingDay,
            int dueDay, BigDecimal creditLimit, Integer defaultPaymentWalletId);

    List<CreditCard> listForOwner(int ownerUserId);

    Optional<CreditCard> findForOwner(int id, int ownerUserId);

    Optional<CreditCard> update(int id, int ownerUserId, String name, String institutionName,
            int closingDay, int dueDay, BigDecimal creditLimit, Integer defaultPaymentWalletId);

    Optional<CreditCard> setActive(int id, int ownerUserId, boolean active);
}
