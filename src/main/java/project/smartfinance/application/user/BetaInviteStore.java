package project.smartfinance.application.user;

import java.time.Instant;

public interface BetaInviteStore {

    boolean isPending(String email);

    /** Marks the invite as used; returns the number of updated rows. */
    int consume(String email, int userId, Instant usedAt);
}
