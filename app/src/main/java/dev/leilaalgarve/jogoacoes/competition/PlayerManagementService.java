package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.competition.exception.CompetitionNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.exception.PlayerNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.exception.PlayerValidationException;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.email.exception.RecipientDomainRejectedException;
import dev.leilaalgarve.jogoacoes.link.LinkCreationResult;
import dev.leilaalgarve.jogoacoes.link.LinkService;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.loginsession.CurrentUserService;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class PlayerManagementService {

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LinkService linkService;
    private final EmailSender emailSender;
    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final AuditLogService auditLogService;

    public PlayerManagementService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                                    LinkService linkService, EmailSender emailSender,
                                    UserService userService, CurrentUserService currentUserService,
                                    AuditLogService auditLogService) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.linkService = linkService;
        this.emailSender = emailSender;
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.auditLogService = auditLogService;
    }

    public List<Participation> listPlayers(Long competitionId, ParticipationStatus statusFilter) {
        findCompetition(competitionId);
        List<Participation> all = participationRepository.findByCompetition_Id(competitionId);
        if (statusFilter == null) {
            return all;
        }
        return all.stream().filter(p -> p.getStatus() == statusFilter).toList();
    }

    /**
     * Spec 05-057: the whole list is rejected, before anything is added or sent, when an e-mail
     * repeats in it or is already in the competition (both ignoring case). An invite racing this
     * one for the same e-mail hits the unique index on {@code saveAndFlush} and gets the same error.
     */
    @Transactional
    public void invitePlayers(Long competitionId, List<String> emails) {
        Competition competition = findCompetition(competitionId);
        rejectRepeatedOrAlreadyIn(competitionId, emails);
        for (String email : emails) {
            Participation participation = new Participation();
            participation.setCompetition(competition);
            participation.setEmail(email);
            // The invited e-mail may already have a registered User from a previous,
            // unrelated competition -- link it now so sendInviteEmail/templateFor knows to
            // send a login link instead of an invite asking them to create an account.
            participation.setUser(userService.findRegisteredByEmail(email).orElse(null));
            participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
            participation.setRequestType(RequestType.INVITE);
            try {
                participation = participationRepository.saveAndFlush(participation);
            } catch (DataIntegrityViolationException invitedMeanwhile) {
                throw new PlayerValidationException(InviteEmails.alreadyInMessage(List.of(email)));
            }
            sendInviteEmailUnlessDomainRejected(participation);
        }
    }

    private void rejectRepeatedOrAlreadyIn(Long competitionId, List<String> emails) {
        List<String> repeated = InviteEmails.repeated(emails);
        if (!repeated.isEmpty()) {
            throw new PlayerValidationException(InviteEmails.repeatedMessage(repeated));
        }
        if (emails.isEmpty()) {
            return;
        }
        List<String> alreadyIn = participationRepository.findEmailsAlreadyIn(competitionId, InviteEmails.normalized(emails));
        if (!alreadyIn.isEmpty()) {
            throw new PlayerValidationException(InviteEmails.alreadyInMessage(alreadyIn));
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
        String email = participation.getEmail();
        // Any LinkRecord created for this participation only carries its id inside extraJson
        // (no FK, see spec 05-003) -- deleting the participation doesn't need to touch it
        // first; a link clicked afterward just fails to resolve the participation, same as
        // any other invalid-link case.
        participationRepository.delete(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participationId, currentUserService.currentUser(),
                "Participation for " + email + " removed from competition");
    }

    @Transactional
    public void resendInviteEmail(Long competitionId, Long participationId) {
        sendInviteEmail(findParticipation(competitionId, participationId));
    }

    @Transactional
    public void resendInviteEmails(Long competitionId, List<Long> participationIds) {
        for (Long participationId : participationIds) {
            sendInviteEmailUnlessDomainRejected(findParticipation(competitionId, participationId));
        }
    }

    /**
     * Spec 05-047: in a group, one address whose domain takes no e-mail doesn't stop the others;
     * that player keeps its status (EmailServiceLoggingAspect logs it). Sent one at a time, the
     * same rejection reaches the caller as 422.
     */
    private void sendInviteEmailUnlessDomainRejected(Participation participation) {
        try {
            sendInviteEmail(participation);
        } catch (RecipientDomainRejectedException rejected) {
            // keeps its status; the next participant is still invited
        }
    }

    private void sendInviteEmail(Participation participation) {
        User user = participation.getUser();
        Long userId = user != null ? user.getId() : null;
        Map<String, String> extra = Map.of(CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY, String.valueOf(participation.getId()));
        LinkCreationResult created = linkService.create(CompetitionLinkHandler.KEY,
                new LinkPayload(userId, participation.getEmail(), extra));
        auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), currentUserService.currentUser(),
                "Invite login link issued to " + participation.getEmail());

        emailSender.send(emailFor(participation, created.url()));

        if (participation.getFirstEmailSentDate() == null) {
            participation.setFirstEmailSentDate(LocalDate.now());
        }
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participationRepository.save(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), currentUserService.currentUser(),
                "Participation status changed to EMAIL_SENT");
    }

    private static CompetitionEmail emailFor(Participation participation, String link) {
        User user = participation.getUser();
        String competitionName = participation.getCompetition().getName();
        if (user != null) {
            return CompetitionEmail.loginLink(user.getId(), participation.getEmail(), user.getName(), competitionName,
                    participation.getRequestType(), link);
        }
        return participation.getRequestType() == RequestType.INVITE
                ? CompetitionEmail.invite(null, participation.getEmail(), competitionName, link)
                : CompetitionEmail.registrationLink(null, participation.getEmail(), competitionName, link);
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
