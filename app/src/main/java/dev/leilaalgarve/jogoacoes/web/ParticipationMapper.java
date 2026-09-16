package dev.leilaalgarve.jogoacoes.web;

import dev.leilaalgarve.jogoacoes.api.model.Participation;
import dev.leilaalgarve.jogoacoes.api.model.ParticipationStatus;

final class ParticipationMapper {

    private ParticipationMapper() {
    }

    static Participation toApiModel(dev.leilaalgarve.jogoacoes.domain.Participation participation) {
        Participation model = new Participation()
                .id(participation.getId())
                .competitionId(participation.getCompetition().getId())
                .email(participation.getEmail())
                .status(ParticipationStatus.valueOf(participation.getStatus().name()))
                .requestType(Participation.RequestTypeEnum.valueOf(participation.getRequestType().name()));
        if (participation.getUser() != null) {
            model.userId(participation.getUser().getId());
        }
        if (participation.getFirstEmailSentDate() != null) {
            model.firstEmailSentDate(participation.getFirstEmailSentDate());
        }
        if (participation.getJoinedAt() != null) {
            model.joinedAt(participation.getJoinedAt());
        }
        return model;
    }
}
