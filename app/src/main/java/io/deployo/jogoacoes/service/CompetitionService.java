package io.deployo.jogoacoes.service;

import io.deployo.jogoacoes.api.model.CompetitionCreateRequest;
import io.deployo.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import io.deployo.jogoacoes.domain.CompetitionStatus;
import io.deployo.jogoacoes.domain.CompetitionType;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.LogType;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.email.EmailRequest;
import io.deployo.jogoacoes.email.EmailSender;
import io.deployo.jogoacoes.repository.CompetitionRepository;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import io.deployo.jogoacoes.web.CompetitionNotFoundException;
import io.deployo.jogoacoes.web.CompetitionValidationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CompetitionService {

    private static final int INVITE_LINK_VALIDITY_DAYS = 7;

    private final CompetitionRepository competitionRepository;
    private final ParticipationRepository participationRepository;
    private final LoginLinkRepository loginLinkRepository;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;

    public CompetitionService(CompetitionRepository competitionRepository, ParticipationRepository participationRepository,
                               LoginLinkRepository loginLinkRepository, UserRepository userRepository, EmailSender emailSender,
                               AuditLogService auditLogService) {
        this.competitionRepository = competitionRepository;
        this.participationRepository = participationRepository;
        this.loginLinkRepository = loginLinkRepository;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public io.deployo.jogoacoes.domain.Competition create(CompetitionCreateRequest request) {
        validate(request);

        io.deployo.jogoacoes.domain.Competition competition = new io.deployo.jogoacoes.domain.Competition();
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
        io.deployo.jogoacoes.domain.Competition competition = competitionRepository.findById(competitionId)
                .orElseThrow(() -> new CompetitionNotFoundException(competitionId));

        if (timing == DecideInviteEmailTimingRequest.TimingEnum.LATER) {
            return;
        }

        User admin = currentUser();
        List<Participation> pending = participationRepository.findByCompetition_IdAndStatus(competitionId, ParticipationStatus.EMAIL_NOT_SENT);
        for (Participation participation : pending) {
            String token = UUID.randomUUID().toString();
            LoginLink link = new LoginLink();
            link.setToken(token);
            link.setEmail(participation.getEmail());
            link.setParticipation(participation);
            link.setEmailSentAt(LocalDateTime.now());
            link.setExpiresAt(LocalDateTime.now().plusDays(INVITE_LINK_VALIDITY_DAYS));
            loginLinkRepository.save(link);
            auditLogService.record(LogType.LOGIN_LINK_ISSUED, link.getId(), admin,
                    "Invite login link issued to " + participation.getEmail());

            User user = participation.getUser();
            EmailTemplate template = user != null ? EmailTemplate.LOGIN_LINK : EmailTemplate.INVITE;
            emailSender.send(new EmailRequest(user != null ? user.getId() : null, participation.getEmail(),
                    user != null ? user.getName() : null, competition.getName(), participation.getRequestType(),
                    "/login-links/" + token, template));

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
        if (request.getType() == io.deployo.jogoacoes.api.model.CompetitionType.PRIVATE
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
