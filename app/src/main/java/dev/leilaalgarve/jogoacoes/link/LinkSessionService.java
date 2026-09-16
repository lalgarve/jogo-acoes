package dev.leilaalgarve.jogoacoes.link;

import java.util.Optional;

/**
 * Session-establishment mechanics (writing {@code SecurityContext}, enforcing the per-user
 * device limit, recording a session) are generic — identical regardless of which
 * {@link LinkHandler} produced the outcome — but they need {@code login}'s {@code User}/roles
 * to build an {@code Authentication}. `link` depends only on this interface; the implementation
 * lives in `login`, which is allowed to depend back on `link` (never the other way around).
 */
public interface LinkSessionService {

    /** Empty when nobody is authenticated on the current request/device. */
    Optional<Long> currentAuthenticatedUserId();

    /**
     * Establishes a session for this user on the current request/device, tied to the link
     * (by token) that authenticated them.
     */
    void establish(Long userId, String token);
}
