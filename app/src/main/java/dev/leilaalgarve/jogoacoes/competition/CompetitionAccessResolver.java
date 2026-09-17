package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.model.AccessLevel;

import java.util.Optional;

/**
 * Pure function (spec 05-004) — no I/O, no Spring bean. {@code Optional.empty()} means denied
 * (the caller has no relationship to the competition and isn't an administrator): translates to
 * a {@code 404}, never a response body, so there is no third {@link AccessLevel} value for it.
 */
public final class CompetitionAccessResolver {

    private CompetitionAccessResolver() {
    }

    public static Optional<AccessLevel> resolve(Competition competition, Participation participation, boolean isAdministrator) {
        if (participation != null) {
            if (participation.getStatus() == ParticipationStatus.IN_COMPETITION) {
                boolean active = competition.getStatus() == CompetitionStatus.OPEN;
                return Optional.of(active ? AccessLevel.READ_WRITE : AccessLevel.READ);
            }
            return Optional.of(AccessLevel.READ);
        }
        if (isAdministrator) {
            return Optional.of(AccessLevel.READ);
        }
        return Optional.empty();
    }
}
