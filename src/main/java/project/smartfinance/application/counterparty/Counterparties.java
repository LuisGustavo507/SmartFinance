package project.smartfinance.application.counterparty;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.counterparty.Counterparty;

@Service
public class Counterparties {

    private final CounterpartyStore store;

    public Counterparties(CounterpartyStore store) {
        this.store = store;
    }

    @Transactional
    public Counterparty create(int authenticatedUserId, String name) {
        return store.create(authenticatedUserId, normalizedName(name));
    }

    @Transactional(readOnly = true)
    public List<Counterparty> list(int authenticatedUserId) {
        return store.listForOwner(authenticatedUserId);
    }

    @Transactional
    public Counterparty setActive(int authenticatedUserId, int id, boolean active) {
        return store.setActive(id, authenticatedUserId, active)
                .orElseThrow(CounterpartyNotFoundException::new);
    }

    @Transactional
    public Counterparty rename(int authenticatedUserId, int id, String name) {
        return store.rename(id, authenticatedUserId, normalizedName(name))
                .orElseThrow(CounterpartyNotFoundException::new);
    }

    @Transactional
    public void delete(int authenticatedUserId, int id) {
        if (store.deleteIfUnlinked(id, authenticatedUserId)) return;
        if (store.findForOwner(id, authenticatedUserId).isPresent()) {
            throw new CounterpartyInUseException();
        }
        throw new CounterpartyNotFoundException();
    }

    private String normalizedName(String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Nome obrigatório.");
        }
        return trimmed;
    }
}
