package dev.leilaalgarve.jogoacoes.link;

import java.util.Map;

/**
 * What a {@code LinkHandler} decided: either the link is fully consumed and some user is now
 * authenticated (with generic key/value redirect data for the controller to translate into its
 * own response shape), or the link is still "pending" — the consumer needs a second HTTP round
 * trip ({@code LinkService#complete}) before anything is marked used.
 */
public final class LinkOutcome {

    private final boolean pending;
    private final Long userId;
    private final Map<String, String> redirectData;

    private LinkOutcome(boolean pending, Long userId, Map<String, String> redirectData) {
        this.pending = pending;
        this.userId = userId;
        this.redirectData = redirectData;
    }

    public static LinkOutcome pending() {
        return new LinkOutcome(true, null, Map.of());
    }

    public static LinkOutcome authenticated(Long userId, Map<String, String> redirectData) {
        return new LinkOutcome(false, userId, redirectData);
    }

    public boolean isPending() {
        return pending;
    }

    public Long userId() {
        return userId;
    }

    public Map<String, String> redirectData() {
        return redirectData;
    }
}
