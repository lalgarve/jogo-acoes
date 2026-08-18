package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParticipationRepository extends JpaRepository<Participation, Long> {

    List<Participation> findByCompetition_IdAndStatus(Long competitionId, ParticipationStatus status);

    Optional<Participation> findByCompetition_IdAndUser_Id(Long competitionId, Long userId);

    Optional<Participation> findByCompetition_IdAndEmailAndStatusNot(Long competitionId, String email, ParticipationStatus status);
}
