package dev.leilaalgarve.jogoacoes.repository;

import dev.leilaalgarve.jogoacoes.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
}
