package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByCompetition_IdAndStatus(Long competitionId, ParticipationStatus status);

    Optional<Participation> findByCompetition_IdAndUser_Id(Long competitionId, Long userId);

    Optional<Participation> findByCompetition_IdAndEmailAndStatusNot(Long competitionId, String email, ParticipationStatus status);

    List<Participation> findByCompetition_Id(Long competitionId);

    Optional<Participation> findByIdAndCompetition_Id(Long id, Long competitionId);

    boolean existsByCompetition_IdAndIdNotAndEmailIgnoreCase(Long competitionId, Long id, String email);

    List<Participation> findByUser_Id(Long userId);

    /**
     * Spec 05-038: a user's summary in one SELECT, the counts as correlated subqueries -- each
     * count independent (a JOIN on both participations and created competitions would multiply
     * rows), and PostgreSQL computes them only for the rows returned. Only confirmed entries
     * ({@code IN_COMPETITION}) count, whatever the competition's status. Starts from the
     * {@code User} entity, which this module already maps through {@code Participation.user} and
     * {@code Competition.creator}; no {@code UserRepository} involved (spec 05-029).
     */
    String PLAYER_SUMMARY_SELECT = """
            select new dev.leilaalgarve.jogoacoes.competition.PlayerSummaryView(
                u.id, u.name, u.email,
                (select count(c) from Competition c where c.creator = u),
                (select count(p) from Participation p where p.user = u
                    and p.status = dev.leilaalgarve.jogoacoes.competition.ParticipationStatus.IN_COMPETITION
                    and p.competition.type = dev.leilaalgarve.jogoacoes.competition.CompetitionType.PUBLIC),
                (select count(p) from Participation p where p.user = u
                    and p.status = dev.leilaalgarve.jogoacoes.competition.ParticipationStatus.IN_COMPETITION
                    and p.competition.type = dev.leilaalgarve.jogoacoes.competition.CompetitionType.PRIVATE))
            from User u
            """;

    @Query(PLAYER_SUMMARY_SELECT + " where u.id = :userId")
    Optional<PlayerSummaryView> findPlayerSummaryById(@Param("userId") Long userId);

    @Query(PLAYER_SUMMARY_SELECT + " where lower(u.email) = lower(:email)")
    Optional<PlayerSummaryView> findPlayerSummaryByEmail(@Param("email") String email);

    /**
     * Spec 05-039: the search filters, shared by the page and its count. A null parameter
     * doesn't restrict. Competition type uses the same rule as the counts (a confirmed entry);
     * the domain is compared exactly against the part after {@code @}, so {@code acme.com}
     * doesn't match {@code sub.acme.com} and no {@code like} wildcard can sneak in. The explicit
     * {@code cast} on the domain: a null string parameter otherwise reaches PostgreSQL untyped and
     * {@code lower(...)} fails as {@code lower(bytea)}.
     */
    String PLAYER_SEARCH_FILTERS = """
             where (:competitionType is null or exists (select 1 from Participation p where p.user = u
                    and p.status = dev.leilaalgarve.jogoacoes.competition.ParticipationStatus.IN_COMPETITION
                    and p.competition.type = :competitionType))
               and (cast(:emailDomain as String) is null
                    or lower(substring(u.email, locate('@', u.email) + 1)) = lower(cast(:emailDomain as String)))
               and (:owner is null
                    or (:owner = true and exists (select 1 from Competition c where c.creator = u))
                    or (:owner = false and not exists (select 1 from Competition c where c.creator = u)))
            """;

    /** Ordered by name, then id, so repeating the search never repeats or skips a user across pages. */
    @Query(value = PLAYER_SUMMARY_SELECT + PLAYER_SEARCH_FILTERS + " order by u.name, u.id",
            countQuery = "select count(u) from User u" + PLAYER_SEARCH_FILTERS)
    Page<PlayerSummaryView> searchPlayerSummaries(@Param("competitionType") CompetitionType competitionType,
                                                  @Param("emailDomain") String emailDomain,
                                                  @Param("owner") Boolean owner,
                                                  Pageable pageable);
}
