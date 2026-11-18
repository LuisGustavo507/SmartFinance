package project.smartfinance.application.user;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUser {

    private final UserAccountStore store;
    private final PasswordEncoder passwordEncoder;

    public RegisterUser(UserAccountStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisteredUser register(String name, String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha deve ter no máximo 72 bytes UTF-8.");
        }

        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        if (store.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
        return store.create(name.strip(), normalizedEmail, passwordEncoder.encode(password));
    }
}
