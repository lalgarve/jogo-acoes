package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.Participation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {
}
