package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
}
