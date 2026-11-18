package project.smartfinance.application.entry;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public interface PlannedEntryTransitions {

    int realizeDue(LocalDate today, OffsetDateTime processedAt);

    boolean cancel(int id, int ownerUserId);

    boolean existsForOwner(int id, int ownerUserId);
}
