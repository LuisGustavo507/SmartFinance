package project.smartfinance.application.user;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.infrastructure.audit.SecurityAudit;

@Service
public class RegisterUser {

    private final UserAccountStore store;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAudit audit;

    public RegisterUser(UserAccountStore store, PasswordEncoder passwordEncoder, SecurityAudit audit) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
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
        RegisteredUser user = store.create(name.strip(), normalizedEmail,
                passwordEncoder.encode(password));
        audit.userRegistered(user.id());
        return user;
    }
}
