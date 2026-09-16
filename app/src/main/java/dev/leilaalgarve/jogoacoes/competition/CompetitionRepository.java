package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.competition.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
}
