package project.smartfinance.application.institution;

import java.util.List;
import project.smartfinance.domain.institution.Institution;

public interface InstitutionStore {

    List<Institution> listActive();

    Institution findByName(String name);
}
