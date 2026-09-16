package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.competition.CompetitionLinkHandler;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.login.LoginLinkHandler;
import dev.leilaalgarve.jogoacoes.login.RoleRepository;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Requirement from spec 05-003: every LinkHandler implementation must declare a key that is
 * both non-null and unique. Exercised directly against LinkRouter's construction logic, with
 * the real handlers from `login`/`competition` -- no Spring context needed for this signal.
 */
@ExtendWith(MockitoExtension.class)
class LinkRouterKeyUniquenessTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private ParticipationRepository participationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditLogService auditLogService;

    @Test
    void everyRealHandlerHasANonNullUniqueKey() {
        LoginLinkHandler loginLinkHandler = new LoginLinkHandler(userRoleRepository);
        CompetitionLinkHandler competitionLinkHandler = new CompetitionLinkHandler(
                participationRepository, userRepository, roleRepository, userRoleRepository, auditLogService);

        LinkRouter router = new LinkRouter(List.of(loginLinkHandler, competitionLinkHandler));

        assertThat(loginLinkHandler.key()).isNotBlank();
        assertThat(competitionLinkHandler.key()).isNotBlank();
        assertThat(loginLinkHandler.key()).isNotEqualTo(competitionLinkHandler.key());
        assertThat(router.handlerFor(loginLinkHandler.key())).isSameAs(loginLinkHandler);
        assertThat(router.handlerFor(competitionLinkHandler.key())).isSameAs(competitionLinkHandler);
    }

    @Test
    void twoHandlersDeclaringTheSameKeyFailFastAtConstruction() {
        LinkHandler first = fakeHandler("duplicate-key");
        LinkHandler second = fakeHandler("duplicate-key");

        assertThatThrownBy(() -> new LinkRouter(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate-key");
    }

    private LinkHandler fakeHandler(String key) {
        return new LinkHandler() {
            @Override
            public String key() {
                return key;
            }

            @Override
            public LinkOutcome consume(LinkPayload payload) {
                throw new UnsupportedOperationException();
            }

            @Override
            public LinkOutcome alreadyAuthenticated(Long authenticatedUserId, LinkPayload payload) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
