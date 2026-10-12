package dev.leilaalgarve.jogoacoes.competition;

/**
 * Spec 05-038: one user's summary as read by {@link ParticipationRepository} -- the user's own
 * fields plus the three counts, all from a single SELECT. Mapped to the generated
 * {@code PlayerSummary} by {@link PlayerDirectoryController}.
 */
public record PlayerSummaryView(Long userId, String name, String email, long ownedCount, long publicCount,
                                long privateCount) {
}
