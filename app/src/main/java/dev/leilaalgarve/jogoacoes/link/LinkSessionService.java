package dev.leilaalgarve.jogoacoes.link;

import java.util.Optional;

/**
 * Session-establishment mechanics (writing {@code SecurityContext}, enforcing the per-user
 * device limit, recording a session) are generic — identical regardless of which
 * {@link LinkHandler} produced the outcome — but they need {@code user}'s {@code User}/roles
 * to build an {@code Authentication}. `link` depends only on this interface; the implementation
 * lives in `loginsession`, which is allowed to depend back on `link` (never the other way around).
 */
public interface LinkSessionService {

    /** Empty when nobody is authenticated on the current request/device. */
    Optional<Long> currentAuthenticatedUserId();

    /**
     * Establishes a session for this user on the current request/device, tied to the link
     * that authenticated them. Takes the already-loaded record rather than its token, so the
     * implementation never has to reach into `link`'s repository (spec 05-029).
     */
    void establish(Long userId, LinkRecord linkRecord);
}
