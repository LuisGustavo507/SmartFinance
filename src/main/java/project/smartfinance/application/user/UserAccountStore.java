package project.smartfinance.application.user;

import java.util.Optional;

public interface UserAccountStore {

    boolean existsByEmail(String email);

    RegisteredUser create(String name, String email, String passwordHash);

    Optional<UserCredentials> findByEmail(String email);

    Optional<UserCredentials> findById(int id);
}
