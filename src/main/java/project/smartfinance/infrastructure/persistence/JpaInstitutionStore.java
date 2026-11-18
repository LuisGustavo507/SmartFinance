package project.smartfinance.infrastructure.persistence;

import java.util.List;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.institution.InstitutionStore;
import project.smartfinance.domain.institution.Institution;
import project.smartfinance.infrastructure.persistence.generated.InstituicaoFinanceira;

@Repository
class JpaInstitutionStore implements InstitutionStore {

    private final EntityManager entityManager;

    JpaInstitutionStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<Institution> listActive() {
        return entityManager.createQuery("""
                select i from InstituicaoFinanceira i
                where i.ativa = true
                order by i.nome, i.id
                """, InstituicaoFinanceira.class)
                .getResultStream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Institution findByName(String name) {
        return entityManager.createQuery("""
                select i from InstituicaoFinanceira i
                where i.ativa = true and lower(trim(i.nome)) = :name
                """, InstituicaoFinanceira.class)
                .setParameter("name", name.strip().toLowerCase())
                .getResultStream()
                .findFirst()
                .map(this::toDomain)
                .orElse(null);
    }

    private Institution toDomain(InstituicaoFinanceira entity) {
        return new Institution(entity.getId(), entity.getCodigo(), entity.getNome(),
                entity.isAtiva());
    }
}
