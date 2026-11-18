package project.smartfinance.application.counterparty;

import java.util.List;
import java.util.Optional;
import project.smartfinance.domain.counterparty.Counterparty;

public interface CounterpartyStore {

    Counterparty create(int ownerUserId, String name);

    List<Counterparty> listForOwner(int ownerUserId);

    Optional<Counterparty> findForOwner(int id, int ownerUserId);

    Optional<Counterparty> setActive(int id, int ownerUserId, boolean active);

    Optional<Counterparty> rename(int id, int ownerUserId, String name);

    boolean deleteIfUnlinked(int id, int ownerUserId);
}
