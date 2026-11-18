package project.smartfinance.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import project.smartfinance.application.user.RegisteredUser;
import project.smartfinance.application.user.UserAccountStore;
import project.smartfinance.application.user.UserCredentials;
import project.smartfinance.infrastructure.persistence.generated.Usuario;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
class JpaUserAccountStore implements UserAccountStore {

    private final EntityManager entityManager;

    JpaUserAccountStore(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean existsByEmail(String email) {
        return !entityManager.createQuery(
                "select u.id from Usuario u where u.email = :email", Integer.class)
                .setParameter("email", email)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    @Override
    public RegisteredUser create(String name, String email, String passwordHash) {
        var user = new Usuario();
        user.setNome(name);
        user.setEmail(email);
        user.setSenhaHash(passwordHash);
        user.setStatus("ATIVO");
        user.setCriadoEm(OffsetDateTime.now(ZoneOffset.UTC));
        entityManager.persist(user);
        entityManager.flush();
        return new RegisteredUser(user.getId(), user.getNome(), user.getEmail());
    }

    @Override
    public Optional<UserCredentials> findByEmail(String email) {
        return entityManager.createQuery(
                        "select u from Usuario u where u.email = :email", Usuario.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .map(this::toCredentials);
    }

    @Override
    public Optional<UserCredentials> findById(int id) {
        return Optional.ofNullable(entityManager.find(Usuario.class, id))
                .map(this::toCredentials);
    }

    private UserCredentials toCredentials(Usuario user) {
        return new UserCredentials(user.getId(), user.getNome(), user.getEmail(),
                user.getSenhaHash(), "ATIVO".equals(user.getStatus()));
    }
}
