package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.Participation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {
}
