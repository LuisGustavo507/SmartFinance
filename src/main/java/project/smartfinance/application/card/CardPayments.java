package project.smartfinance.application.card;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.wallet.WalletStore;
import project.smartfinance.domain.card.CardPayment;
import project.smartfinance.domain.card.CardStatement;
import project.smartfinance.domain.wallet.Wallet;

@Service
public class CardPayments {

    private final CardPaymentStore store;
    private final CardPurchaseStore statements;
    private final CardStatementTransitions transitions;
    private final WalletStore wallets;
    private final Clock clock;

    public CardPayments(CardPaymentStore store, CardPurchaseStore statements,
            CardStatementTransitions transitions, WalletStore wallets, Clock clock) {
        this.store = store;
        this.statements = statements;
        this.transitions = transitions;
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    public CardPayment pay(int ownerUserId, int statementId, int walletId,
            BigDecimal amount, OffsetDateTime paidOn) {
        // The cycle boundary in UTC prevails over a stale status in the database.
        transitions.regularize(LocalDate.now(clock), OffsetDateTime.now(clock));
        CardStatement statement = statements.findStatement(ownerUserId, statementId)
                .orElseThrow(CardReferenceNotFoundException::new);
        if (!"FECHADA".equals(statement.status()) && !"ATRASADA".equals(statement.status())) {
            throw new CardPaymentNotAllowedException();
        }
        BigDecimal due = statement.closedAmount() == null ? statement.totalAmount()
                : statement.closedAmount();
        if (due == null || due.signum() <= 0) {
            throw new CardPaymentNotAllowedException();
        }
        if (amount != null && amount.compareTo(due) != 0) {
            throw new IllegalArgumentException("O pagamento deve ser integral.");
        }
        Wallet wallet = wallets.findForOwner(walletId, ownerUserId)
                .orElseThrow(CardReferenceNotFoundException::new);
        if (!wallet.active()) {
            throw new CardReferenceNotFoundException();
        }
        LocalDate paidDay = paidOn.atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        if (paidDay.isBefore(statement.closingDate().plusDays(1))) {
            throw new IllegalArgumentException(
                    "O pagamento não pode ser antecipado ao fechamento da fatura.");
        }
        if (paidOn.isAfter(OffsetDateTime.now(clock))) {
            throw new IllegalArgumentException("A data do pagamento não pode ser futura.");
        }
        if (paidDay.isBefore(wallet.initialBalanceDate())) {
            throw new IllegalArgumentException(
                    "A data do pagamento não pode anteceder o saldo inicial da conta.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        CardPayment payment = store.create(ownerUserId, statementId, walletId, due, paidOn, now);
        statements.markPaid(ownerUserId, statementId);
        return payment;
    }
}
