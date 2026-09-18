package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.link.exception.LoginLinkInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the risk plan.md calls out for the two-phase flow: {@code complete} must fail in a
 * controlled way when called on a token that isn't in the "pending" state anymore (whether
 * because it was already completed, or because {@code consume} already resolved it outright).
 */
@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

    @Mock
    private LinkRecordRepository linkRecordRepository;

    @Mock
    private LinkRouter linkRouter;

    @Mock
    private LinkSessionService linkSessionService;

    @Test
    void completeFailsWhenTheLinkIsAlreadyUsed() {
        LinkRecord record = new LinkRecord();
        record.setToken("some-token");
        record.setServiceKey("competition-entry");
        record.setEmail("player@example.com");
        record.setExpiresAt(LocalDateTime.now().plusDays(1));
        record.setUsedAt(LocalDateTime.now().minusMinutes(1));
        when(linkRecordRepository.findByToken("some-token")).thenReturn(Optional.of(record));

        LinkService linkService = new LinkService(linkRecordRepository, linkRouter, linkSessionService, jacksonObjectMapper());

        assertThatThrownBy(() -> linkService.complete("some-token", Map.of("name", "New Player")))
                .isInstanceOf(LoginLinkInvalidException.class);

        verifyNoInteractions(linkRouter, linkSessionService);
    }

    private tools.jackson.databind.ObjectMapper jacksonObjectMapper() {
        return tools.jackson.databind.json.JsonMapper.builder().build();
    }
}
