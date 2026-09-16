package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.CompetitionsApi;
import dev.leilaalgarve.jogoacoes.api.MyCompetitionsApi;
import dev.leilaalgarve.jogoacoes.api.model.Competition;
import dev.leilaalgarve.jogoacoes.api.model.CompetitionCreateRequest;
import dev.leilaalgarve.jogoacoes.api.model.CompetitionDetail;
import dev.leilaalgarve.jogoacoes.api.model.CompetitionSummary;
import dev.leilaalgarve.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import dev.leilaalgarve.jogoacoes.api.model.MyCompetitions;
import dev.leilaalgarve.jogoacoes.competition.CompetitionService;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.NativeWebRequest;

import java.util.List;
import java.util.Optional;

@RestController
public class CompetitionsController implements CompetitionsApi, MyCompetitionsApi {

    private final CompetitionService competitionService;
    private final CompetitionViewService competitionViewService;
    private final UserRepository userRepository;

    public CompetitionsController(CompetitionService competitionService, CompetitionViewService competitionViewService,
                                   UserRepository userRepository) {
        this.competitionService = competitionService;
        this.competitionViewService = competitionViewService;
        this.userRepository = userRepository;
    }

    /** Both generated interfaces declare this default the same way (empty) -- resolves the diamond. */
    @Override
    public Optional<NativeWebRequest> getRequest() {
        return CompetitionsApi.super.getRequest();
    }

    @Override
    public ResponseEntity<Competition> createCompetition(CompetitionCreateRequest competitionCreateRequest) {
        dev.leilaalgarve.jogoacoes.competition.Competition created = competitionService.create(competitionCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(toApiModel(created));
    }

    @Override
    public ResponseEntity<Void> decideInviteEmailTiming(Long competitionId, DecideInviteEmailTimingRequest decideInviteEmailTimingRequest) {
        competitionService.decideInviteEmailTiming(competitionId, decideInviteEmailTimingRequest.getTiming());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<CompetitionSummary>> listPublicCompetitions() {
        return ResponseEntity.ok(competitionViewService.listPublicCompetitions());
    }

    @Override
    public ResponseEntity<MyCompetitions> listMyCompetitions() {
        return ResponseEntity.ok(competitionViewService.listMyCompetitions(currentUser().getId()));
    }

    @Override
    public ResponseEntity<CompetitionDetail> getCompetitionDetail(Long competitionId) {
        return competitionViewService.getCompetitionDetail(competitionId, currentUser().getId(), isAdministrator())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private User currentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + email));
    }

    private boolean isAdministrator() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + RoleName.ADMINISTRATOR));
    }

    private static Competition toApiModel(dev.leilaalgarve.jogoacoes.competition.Competition competition) {
        return new Competition()
                .id(competition.getId())
                .name(competition.getName())
                .type(dev.leilaalgarve.jogoacoes.api.model.CompetitionType.valueOf(competition.getType().name()))
                .startDate(competition.getStartDate())
                .durationDays(competition.getDurationDays())
                .recurring(competition.isRecurring())
                .buyFee(competition.getBuyFee().doubleValue())
                .sellFee(competition.getSellFee().doubleValue())
                .status(dev.leilaalgarve.jogoacoes.api.model.CompetitionStatus.valueOf(competition.getStatus().name()))
                .creatorId(competition.getCreator().getId());
    }
}
