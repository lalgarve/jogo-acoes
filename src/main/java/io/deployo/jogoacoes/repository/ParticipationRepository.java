package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByCompetition_IdAndStatus(Long competitionId, ParticipationStatus status);
}
