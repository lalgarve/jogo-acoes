package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.log.AuditLogService;

import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.link.LinkCreationResult;
import dev.leilaalgarve.jogoacoes.link.LinkService;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.email.EmailRequest;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.competition.CompetitionNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.PlayerNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.PlayerValidationException;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public PlayerManagementService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                                    LinkService linkService, EmailSender emailSender,
                                    UserRepository userRepository, AuditLogService auditLogService) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.linkService = linkService;
        this.emailSender = emailSender;
        this.userRepository = userRepository;
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

    @Transactional
    public void invitePlayers(Long competitionId, List<String> emails) {
        Competition competition = findCompetition(competitionId);
        for (String email : emails) {
            Participation participation = new Participation();
            participation.setCompetition(competition);
            participation.setEmail(email);
            // The invited e-mail may already have a registered User from a previous,
            // unrelated competition -- link it now so sendInviteEmail/templateFor knows to
            // send a login link instead of an invite asking them to create an account.
            participation.setUser(userRepository.findByEmail(email).filter(User::isRegistered).orElse(null));
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
        String email = participation.getEmail();
        // Any LinkRecord created for this participation only carries its id inside extraJson
        // (no FK, see spec 05-003) -- deleting the participation doesn't need to touch it
        // first; a link clicked afterward just fails to resolve the participation, same as
        // any other invalid-link case.
        participationRepository.delete(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participationId, currentUser(),
                "Participation for " + email + " removed from competition");
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
        User user = participation.getUser();
        Long userId = user != null ? user.getId() : null;
        Map<String, String> extra = Map.of(CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY, String.valueOf(participation.getId()));
        LinkCreationResult created = linkService.create(CompetitionLinkHandler.KEY,
                new LinkPayload(userId, participation.getEmail(), extra));
        auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), currentUser(),
                "Invite login link issued to " + participation.getEmail());

        emailSender.send(new EmailRequest(userId, participation.getEmail(),
                user != null ? user.getName() : null, participation.getCompetition().getName(), participation.getRequestType(),
                "/login-links/" + created.token(), templateFor(participation)));

        if (participation.getFirstEmailSentDate() == null) {
            participation.setFirstEmailSentDate(LocalDate.now());
        }
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participationRepository.save(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), currentUser(),
                "Participation status changed to EMAIL_SENT");
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

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
