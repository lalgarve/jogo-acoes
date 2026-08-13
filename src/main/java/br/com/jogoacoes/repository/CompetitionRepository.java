package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
}
