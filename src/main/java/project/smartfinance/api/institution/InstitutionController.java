package project.smartfinance.api.institution;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import project.smartfinance.application.institution.Institutions;
import project.smartfinance.domain.institution.Institution;

@RestController
@RequestMapping("/api/institutions")
class InstitutionController {

    private final Institutions institutions;

    InstitutionController(Institutions institutions) {
        this.institutions = institutions;
    }

    @GetMapping
    List<Institution> list(Authentication authentication) {
        return institutions.list();
    }
}
