package project.smartfinance.application.user;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.smartfinance.infrastructure.audit.SecurityAudit;

@Service
public class RegisterUser {

    private final UserAccountStore store;
    private final BetaInviteStore invites;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAudit audit;
    private final Clock clock;
    private final boolean inviteRequired;

    public RegisterUser(UserAccountStore store, BetaInviteStore invites,
            PasswordEncoder passwordEncoder, SecurityAudit audit, Clock clock,
            @Value("${smartfinance.registration.invite-required:true}") boolean inviteRequired) {
        this.store = store;
        this.invites = invites;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
        this.inviteRequired = inviteRequired;
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
        if (inviteRequired && !invites.isPending(normalizedEmail)) {
            audit.registrationRejected();
            throw new InviteRequiredException();
        }
        RegisteredUser user = store.create(name.strip(), normalizedEmail,
                passwordEncoder.encode(password));
        // Consume inside the same transaction: a failure rolls the account back.
        if (inviteRequired && invites.consume(normalizedEmail, user.id(), clock.instant()) != 1) {
            audit.registrationRejected();
            throw new InviteRequiredException();
        }
        audit.userRegistered(user.id());
        return user;
    }
}
