package project.smartfinance.application.payable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.payable.Payable;
import project.smartfinance.domain.payable.PayablePayment;

public interface PayableStore {
    Payable create(int ownerId, int categoryId, Integer entityId, String description,
            String necessity, BigDecimal amount, LocalDate dueOn, OffsetDateTime now, LocalDate today);
    Optional<Payable> find(int ownerId, int id, LocalDate today);
    List<Payable> list(int ownerId, LocalDate month, LocalDate today);
    void lock(int ownerId, int id);
    Payable update(int ownerId, int id, int categoryId, Integer entityId,
            String description, String necessity, BigDecimal amount, LocalDate dueOn, LocalDate today);
    void cancel(int ownerId, int id, OffsetDateTime now);
    PayablePayment pay(int ownerId, int id, int walletId, BigDecimal amount,
            LocalDate paidOn, OffsetDateTime now);
    Optional<PayablePayment> payment(int ownerId, int payableId, int paymentId);
    List<PayablePayment> payments(int ownerId, int payableId);
    void reverse(int ownerId, int paymentId, OffsetDateTime now, String reason);
}
