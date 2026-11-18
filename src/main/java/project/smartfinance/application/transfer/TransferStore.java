package project.smartfinance.application.transfer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import project.smartfinance.domain.transfer.Transfer;

public interface TransferStore {

    Transfer create(int ownerUserId, int sourceWalletId, int destinationWalletId,
            BigDecimal amount, String description, OffsetDateTime transferredAt,
            OffsetDateTime createdAt);

    List<Transfer> listForOwnerBetween(int ownerUserId, OffsetDateTime from,
            OffsetDateTime untilExclusive);
}
