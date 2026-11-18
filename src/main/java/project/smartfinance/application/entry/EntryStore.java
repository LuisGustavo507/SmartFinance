package project.smartfinance.application.entry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.category.Category.Kind;
import project.smartfinance.domain.entry.Entry;
import project.smartfinance.domain.expense.Necessity;

public interface EntryStore {

    Entry create(int ownerUserId, int walletId, int categoryId, Kind kind,
            String description, BigDecimal amount, LocalDate occurredOn,
            OffsetDateTime recordedAt, Integer entityId, String entityName, Necessity necessity);

    Entry createPlanned(int ownerUserId, int walletId, int categoryId, Kind kind,
            String description, BigDecimal amount, LocalDate expectedOn,
            OffsetDateTime createdAt, Integer entityId, String entityName, Necessity necessity);

    List<Entry> createSeries(int ownerUserId, int walletId, int categoryId, Kind kind,
            String description, BigDecimal amount, List<LocalDate> expectedDates,
            OffsetDateTime createdAt, Integer entityId, String entityName, Necessity necessity);

    List<Entry> listForOwnerAndMonth(int ownerUserId, LocalDate firstDayOfMonth,
            Integer entityId);

    List<Entry> listForOwnerAndSeries(int ownerUserId, int seriesId);

    Optional<Entry> findForOwner(int ownerUserId, int id);

    Entry updatePlanned(int ownerUserId, int id, int walletId, int categoryId, Kind kind,
            String description, BigDecimal amount, LocalDate expectedOn,
            Integer entityId, String entityName, Necessity necessity);

    Optional<Entry> updateEntityLink(int ownerUserId, int id, Integer entityId);
}
