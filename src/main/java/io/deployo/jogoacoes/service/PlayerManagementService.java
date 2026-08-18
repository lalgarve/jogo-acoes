package io.deployo.jogoacoes.service;

import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.email.EmailSender;
import io.deployo.jogoacoes.repository.CompetitionRepository;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.web.CompetitionNotFoundException;
import io.deployo.jogoacoes.web.PlayerNotFoundException;
import io.deployo.jogoacoes.web.PlayerValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PlayerManagementService {

    private static final int LINK_VALIDITY_DAYS = 7;

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LoginLinkRepository loginLinkRepository;
    private final EmailSender emailSender;

    public PlayerManagementService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                                    LoginLinkRepository loginLinkRepository, EmailSender emailSender) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.loginLinkRepository = loginLinkRepository;
        this.emailSender = emailSender;
    }

    public List<Participation> listPlayers(Long competitionId, ParticipationStatus statusFilter) {
        findCompetition(competitionId);
        List<Participation> all = participationRepository.findByCompetition_Id(competitionId);
        if (statusFilter == null) {
            return all;
        }
        return all.stream().filter(p -> p.getStatus() == statusFilter).toList();
    }

    @Transactional
    public void invitePlayers(Long competitionId, List<String> emails) {
        Competition competition = findCompetition(competitionId);
        for (String email : emails) {
            Participation participation = new Participation();
            participation.setCompetition(competition);
            participation.setEmail(email);
            participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
            participation.setRequestType(RequestType.INVITE);
            participation = participationRepository.save(participation);
            sendInviteEmail(participation);
        }
    }

    @Transactional
    public Participation updateEmail(Long competitionId, Long participationId, String newEmail) {
        Participation participation = findParticipation(competitionId, participationId);
        if (participationRepository.existsByCompetition_IdAndIdNotAndEmailIgnoreCase(competitionId, participationId, newEmail)) {
            throw new PlayerValidationException("E-mail already used by another player in this competition");
        }
        participation.setEmail(newEmail);
        return participationRepository.save(participation);
    }

    @Transactional
    public void removePlayer(Long competitionId, Long participationId) {
        Participation participation = findParticipation(competitionId, participationId);
        participationRepository.delete(participation);
    }

    @Transactional
    public void resendInviteEmail(Long competitionId, Long participationId) {
        sendInviteEmail(findParticipation(competitionId, participationId));
    }

    @Transactional
    public void resendInviteEmails(Long competitionId, List<Long> participationIds) {
        for (Long participationId : participationIds) {
            sendInviteEmail(findParticipation(competitionId, participationId));
        }
    }

    private void sendInviteEmail(Participation participation) {
        String token = UUID.randomUUID().toString();
        LoginLink link = new LoginLink();
        link.setToken(token);
        link.setEmail(participation.getEmail());
        link.setUser(participation.getUser());
        link.setParticipation(participation);
        link.setEmailSentAt(LocalDateTime.now());
        link.setExpiresAt(LocalDateTime.now().plusDays(LINK_VALIDITY_DAYS));
        loginLinkRepository.save(link);

        Long userId = participation.getUser() != null ? participation.getUser().getId() : null;
        emailSender.send(userId, participation.getEmail(), "/login-links/" + token, templateFor(participation));

        if (participation.getFirstEmailSentDate() == null) {
            participation.setFirstEmailSentDate(LocalDate.now());
        }
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participationRepository.save(participation);
    }

    private static EmailTemplate templateFor(Participation participation) {
        if (participation.getUser() != null) {
            return EmailTemplate.LOGIN_LINK;
        }
        return participation.getRequestType() == RequestType.INVITE ? EmailTemplate.INVITE : EmailTemplate.REGISTRATION_LINK;
    }

    private Competition findCompetition(Long competitionId) {
        return competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CompetitionNotFoundException(competitionId));
    }

    private Participation findParticipation(Long competitionId, Long participationId) {
        return participationRepository.findByIdAndCompetition_Id(participationId, competitionId)
                .orElseThrow(() -> new PlayerNotFoundException(competitionId, participationId));
    }
}
