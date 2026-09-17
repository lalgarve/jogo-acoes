package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.competition.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    List<Competition> findByTypeAndStatus(CompetitionType type, CompetitionStatus status);
}
