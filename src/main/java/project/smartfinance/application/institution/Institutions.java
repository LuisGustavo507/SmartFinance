package project.smartfinance.application.institution;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.domain.institution.Institution;

@Service
public class Institutions {

    private final InstitutionStore store;

    public Institutions(InstitutionStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public List<Institution> list() {
        return store.listActive();
    }
}
