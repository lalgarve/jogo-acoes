package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;

import java.util.Map;

/**
 * Implemented by each consumer module (`login`, `competition`), never by `link` itself — that's
 * what inverts the dependency the old direct {@code LoginLink}/{@code Participation} FK had.
 */
public interface LinkHandler {

    /** The value stored as the link's service key. Must be unique across every implementation. */
    String key();

    /** Normal first-touch consumption of a not-yet-used link. */
    LinkOutcome consume(LinkPayload payload);

    /**
     * Second phase for a link whose {@link #consume} returned {@link LinkOutcome#pending()} —
     * e.g. finishing registration. Implementations that are always a single phase (like plain
     * login) never override this.
     */
    default LinkOutcome complete(LinkPayload payload, Map<String, String> extra) {
        throw new UnsupportedOperationException(key() + " does not support a completion phase");
    }

    /**
     * Called instead of {@link #consume} when the caller already has a valid session on this
     * device — the link's own used/expired state is irrelevant then, this only computes where
     * to redirect the already-authenticated user.
     */
    LinkOutcome alreadyAuthenticated(Long authenticatedUserId, LinkPayload payload);
}
