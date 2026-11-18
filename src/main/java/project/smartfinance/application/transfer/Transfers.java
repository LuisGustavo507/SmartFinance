package project.smartfinance.application.transfer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.application.wallet.WalletStore;
import project.smartfinance.domain.transfer.Transfer;
import project.smartfinance.domain.wallet.Wallet;

@Service
public class Transfers {

    private final TransferStore store;
    private final WalletStore wallets;
    private final Clock clock;

    public Transfers(TransferStore store, WalletStore wallets, Clock clock) {
        this.store = store;
        this.wallets = wallets;
        this.clock = clock;
    }

    @Transactional
    public Transfer create(int ownerId, int sourceWalletId, int destinationWalletId,
            BigDecimal amount, String description, OffsetDateTime transferredAt) {
        if (sourceWalletId == destinationWalletId || amount.signum() <= 0
                || amount.scale() > 2 || transferredAt.toInstant().isAfter(clock.instant())) {
            throw new IllegalArgumentException("Transferência inválida.");
        }
        Wallet source = wallets.findForOwner(sourceWalletId, ownerId)
                .filter(Wallet::active)
                .orElseThrow(TransferReferenceNotFoundException::new);
        Wallet destination = wallets.findForOwner(destinationWalletId, ownerId)
                .filter(Wallet::active)
                .orElseThrow(TransferReferenceNotFoundException::new);
        OffsetDateTime actualUtc = transferredAt.withOffsetSameInstant(ZoneOffset.UTC);
        if (actualUtc.toLocalDate().isBefore(source.initialBalanceDate())
                || actualUtc.toLocalDate().isBefore(destination.initialBalanceDate())) {
            throw new IllegalArgumentException("A transferência antecede o saldo inicial.");
        }
        String trimmed = description == null ? null : description.strip();
        return store.create(ownerId, sourceWalletId, destinationWalletId,
                amount, trimmed == null || trimmed.isEmpty() ? null : trimmed,
                actualUtc, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public List<Transfer> list(int ownerId, YearMonth month) {
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return store.listForOwnerBetween(ownerId, from, from.plusMonths(1));
    }
}
