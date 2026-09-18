package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.log.AuditLogService;

import dev.leilaalgarve.jogoacoes.api.model.CompetitionCreateRequest;
import dev.leilaalgarve.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
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
import dev.leilaalgarve.jogoacoes.competition.exception.CompetitionNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.exception.CompetitionValidationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LinkService linkService;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;

    public CompetitionService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                               LinkService linkService, UserRepository userRepository, EmailSender emailSender,
                               AuditLogService auditLogService) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.linkService = linkService;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public dev.leilaalgarve.jogoacoes.competition.Competition create(CompetitionCreateRequest request) {
        validate(request);

        dev.leilaalgarve.jogoacoes.competition.Competition competition = new dev.leilaalgarve.jogoacoes.competition.Competition();
        CompetitionType type = CompetitionType.valueOf(request.getType().name());
        competition.setName(request.getName());
        competition.setType(type);
        competition.setStartDate(request.getStartDate());
        competition.setDurationDays(request.getDurationDays());
        competition.setRecurring(Boolean.TRUE.equals(request.getRecurring()));
        competition.setBuyFee(BigDecimal.valueOf(request.getBuyFee()));
        competition.setSellFee(BigDecimal.valueOf(request.getSellFee()));
        User creator = currentUser();
        competition.setCreator(creator);
        competition.setStatus(type == CompetitionType.PUBLIC ? CompetitionStatus.OPEN : CompetitionStatus.AWAITING_INVITES);
        competition = competitionRepository.save(competition);
        auditLogService.record(LogType.COMPETITION_CREATED, competition.getId(), creator,
                "Competition \"" + competition.getName() + "\" created");

        if (type == CompetitionType.PRIVATE) {
            for (String email : request.getEmails()) {
                Participation participation = new Participation();
                participation.setCompetition(competition);
                participation.setEmail(email);
                // The invited e-mail may already have a registered User from a previous,
                // unrelated competition -- link it now so decideInviteEmailTiming knows to
                // send a login link instead of an invite asking them to create an account.
                participation.setUser(userRepository.findByEmail(email).filter(User::isRegistered).orElse(null));
                participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
                participation.setRequestType(RequestType.INVITE);
                participationRepository.save(participation);
                auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), creator,
                        "Participation for " + email + " created with status EMAIL_NOT_SENT");
            }
        }

        return competition;
    }

    @Transactional
    public void decideInviteEmailTiming(Long competitionId, DecideInviteEmailTimingRequest.TimingEnum timing) {
        dev.leilaalgarve.jogoacoes.competition.Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CompetitionNotFoundException(competitionId));

        if (timing == DecideInviteEmailTimingRequest.TimingEnum.LATER) {
            return;
        }

        User admin = currentUser();
        List<Participation> pending = participationRepository.findByCompetition_IdAndStatus(competitionId, ParticipationStatus.EMAIL_NOT_SENT);
        for (Participation participation : pending) {
            User user = participation.getUser();
            Long userId = user != null ? user.getId() : null;
            // decideInviteEmailTiming never links a User directly to the link itself (unlike
            // the other two competition-entry call sites) -- it's read off participation.getUser()
            // here, at creation time, since LinkRecord no longer carries a Participation to
            // fall back to at consumption time (see spec 05-003).
            Map<String, String> extra = Map.of(CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY, String.valueOf(participation.getId()));
            LinkCreationResult created = linkService.create(CompetitionLinkHandler.KEY,
                    new LinkPayload(userId, participation.getEmail(), extra));
            auditLogService.record(LogType.LOGIN_LINK_ISSUED, created.id(), admin,
                    "Invite login link issued to " + participation.getEmail());

            EmailTemplate template = user != null ? EmailTemplate.LOGIN_LINK : EmailTemplate.INVITE;
            emailSender.send(new EmailRequest(userId, participation.getEmail(),
                    user != null ? user.getName() : null, competition.getName(), participation.getRequestType(),
                    "/login-links/" + created.token(), template));

            participation.setStatus(ParticipationStatus.EMAIL_SENT);
            participation.setFirstEmailSentDate(LocalDate.now());
            participationRepository.save(participation);
            auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), admin,
                    "Participation status changed to EMAIL_SENT");
        }

        competition.setStatus(CompetitionStatus.OPEN);
        competitionRepository.save(competition);
    }

    private void validate(CompetitionCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new CompetitionValidationException("Missing name");
        }
        if (request.getType() == dev.leilaalgarve.jogoacoes.api.model.CompetitionType.PRIVATE
                && (request.getEmails() == null || request.getEmails().isEmpty())) {
            throw new CompetitionValidationException("Missing e-mail list");
        }
        if (request.getStartDate() == null || !request.getStartDate().isAfter(LocalDate.now())) {
            throw new CompetitionValidationException("Invalid start date");
        }
        if (request.getDurationDays() == null || request.getDurationDays() <= 0) {
            throw new CompetitionValidationException("Invalid duration");
        }
        if (request.getBuyFee() == null || request.getBuyFee() < 0
                || request.getSellFee() == null || request.getSellFee() < 0) {
            throw new CompetitionValidationException("Invalid brokerage fee");
        }
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }
}
