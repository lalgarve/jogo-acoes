package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-058: which active link a repeated request resends, against the real Postgres. Each test
 * uses its own e-mail and user id, so rows left by other tests never match.
 */
@SpringBootTest
class LinkServiceIntegrationTest {

    private static final String KEY = "login";

    @Autowired
    private LinkService linkService;

    @Autowired
    private LinkRecordRepository linkRecordRepository;

    @Test
    void findsTheActiveLinkWithTheSameKeyEmailUserAndExtra() {
        String email = unique("resend");
        Long userId = newUserId();
        LinkRecord link = save(KEY, userId, email, "{\"returnTo\":\"/competitions/mine\"}", inDays(6));

        assertThat(linkService.findActive(KEY, new LinkPayload(userId, email.toUpperCase(), Map.of("returnTo", "/competitions/mine"))))
                .hasValueSatisfying(found -> {
                    assertThat(found.id()).isEqualTo(link.getId());
                    assertThat(found.token()).isEqualTo(link.getToken());
                    assertThat(found.url()).endsWith("/login-links/" + link.getToken());
                });
    }

    @Test
    void anExtraWithTheSameEntriesInAnotherOrderStillMatches() {
        String email = unique("resend-order");
        LinkRecord link = save(KEY, null, email, "{\"b\":\"2\",\"a\":\"1\"}", inDays(6));

        assertThat(linkService.findActive(KEY, new LinkPayload(null, email, Map.of("a", "1", "b", "2"))))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(link.getId()));
    }

    @Test
    void linksThatCannotBeResentAreNotFound() {
        String email = unique("not-resendable");
        Long userId = newUserId();
        LinkPayload payload = new LinkPayload(userId, email, Map.of());
        save(KEY, userId, email, "{\"returnTo\":\"/admin\"}", inDays(6));
        save("competition-entry", userId, email, "{}", inDays(6));
        save(KEY, newUserId(), email, "{}", inDays(6));
        save(KEY, userId, email, "{}", LocalDateTime.now().plusHours(20));
        save(KEY, userId, email, "{}", LocalDateTime.now().minusMinutes(1));
        LinkRecord used = save(KEY, userId, email, "{}", inDays(6));
        used.setUsedAt(LocalDateTime.now());
        linkRecordRepository.save(used);
        LinkRecord invalidated = save(KEY, userId, email, "{}", inDays(6));
        invalidated.setInvalidatedAt(LocalDateTime.now());
        linkRecordRepository.save(invalidated);

        assertThat(linkService.findActive(KEY, payload)).isEmpty();
    }

    @Test
    void withTwoCandidatesTheMostRecentIsFound() {
        String email = unique("two-candidates");
        Long userId = newUserId();
        save(KEY, userId, email, "{}", inDays(5));
        LinkRecord newer = save(KEY, userId, email, "{}", inDays(6));

        assertThat(linkService.findActive(KEY, new LinkPayload(userId, email, Map.of())))
                .hasValueSatisfying(found -> assertThat(found.id()).isEqualTo(newer.getId()));
    }

    @Test
    void markResentOnlyChangesWhenTheEmailWasSent() {
        String email = unique("mark-resent");
        LinkRecord before = save(KEY, newUserId(), email, "{}", inDays(6));

        linkService.markResent(before.getId());

        LinkRecord after = linkRecordRepository.findById(before.getId()).orElseThrow();
        assertThat(after.getEmailSentAt()).isAfter(before.getEmailSentAt());
        assertThat(after.getToken()).isEqualTo(before.getToken());
        assertThat(after.getExpiresAt()).isEqualTo(before.getExpiresAt());
        assertThat(after.getUsedAt()).isNull();
        assertThat(after.getInvalidatedAt()).isNull();
    }

    @Test
    void markResentOfAnUnknownLinkFails() {
        assertThatThrownBy(() -> linkService.markResent(-1L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void invalidatingActiveLinksSparesTheOnesExpiringWithinADay() {
        String email = unique("invalidate");
        Long userId = newUserId();
        LinkRecord longLived = save(KEY, userId, email, "{}", inDays(6));
        LinkRecord expiringSoon = save(KEY, userId, email, "{}", LocalDateTime.now().plusHours(20));

        linkService.invalidateActiveLinksFor(userId);

        assertThat(linkRecordRepository.findById(longLived.getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(linkRecordRepository.findById(expiringSoon.getId()).orElseThrow().getInvalidatedAt()).isNull();
    }

    private LinkRecord save(String serviceKey, Long userId, String email, String extraJson, LocalDateTime expiresAt) {
        LinkRecord link = new LinkRecord();
        link.setToken(UUID.randomUUID().toString());
        link.setServiceKey(serviceKey);
        link.setUserId(userId);
        link.setEmail(email);
        link.setExtraJson(extraJson);
        link.setEmailSentAt(LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MICROS));
        link.setExpiresAt(expiresAt.truncatedTo(ChronoUnit.MICROS));
        return linkRecordRepository.save(link);
    }

    private static LocalDateTime inDays(int days) {
        return LocalDateTime.now().plusDays(days);
    }

    /** link_record.user_id has no foreign key (the link module doesn't know users), so any unused id will do. */
    private static Long newUserId() {
        return 1_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
    }
}
