package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The generic mechanism behind every magic link: creates a {@link LinkRecord}, and on
 * consumption/completion dispatches to whichever {@link LinkHandler} owns the record's service
 * key (via {@link LinkRouter}), then — for a non-pending outcome — marks the record used and
 * hands off to {@link LinkSessionService} to establish the actual session. Never imports a type
 * from `login`/`competition`.
 */
@Service
public class LinkService {

    private static final int LINK_VALIDITY_DAYS = 7;

    private final LinkRecordRepository linkRecordRepository;
    private final LinkRouter linkRouter;
    private final LinkSessionService linkSessionService;
    private final ObjectMapper objectMapper;

    public LinkService(LinkRecordRepository linkRecordRepository, LinkRouter linkRouter,
                        LinkSessionService linkSessionService, ObjectMapper objectMapper) {
        this.linkRecordRepository = linkRecordRepository;
        this.linkRouter = linkRouter;
        this.linkSessionService = linkSessionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LinkCreationResult create(String serviceKey, LinkPayload payload) {
        LinkRecord record = new LinkRecord();
        record.setToken(UUID.randomUUID().toString());
        record.setServiceKey(serviceKey);
        record.setUserId(payload.userId());
        record.setEmail(payload.email());
        record.setExtraJson(writeExtra(payload.extra()));
        record.setEmailSentAt(LocalDateTime.now());
        record.setExpiresAt(LocalDateTime.now().plusDays(LINK_VALIDITY_DAYS));
        record = linkRecordRepository.save(record);
        return new LinkCreationResult(record.getId(), record.getToken());
    }

    @Transactional
    public LinkOutcome consume(String token) {
        LinkRecord record = findValidRecord(token);
        LinkHandler handler = linkRouter.handlerFor(record.getServiceKey());
        LinkPayload payload = toPayload(record);

        Optional<Long> currentUserId = linkSessionService.currentAuthenticatedUserId();
        if (currentUserId.isPresent()) {
            return handler.alreadyAuthenticated(currentUserId.get(), payload);
        }

        if (record.getUsedAt() != null) {
            throw new LoginLinkUsedOnAnotherDeviceException("Link already used to log in on a different device");
        }

        LinkOutcome outcome = handler.consume(payload);
        finishIfNotPending(record, outcome);
        return outcome;
    }

    @Transactional
    public LinkOutcome complete(String token, Map<String, String> extra) {
        LinkRecord record = findValidRecord(token);
        if (record.getUsedAt() != null) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }
        LinkHandler handler = linkRouter.handlerFor(record.getServiceKey());
        LinkOutcome outcome = handler.complete(toPayload(record), extra);
        finishIfNotPending(record, outcome);
        return outcome;
    }

    /** Previous active (unused, not invalidated) links for this user are invalidated before requesting a new one. */
    @Transactional
    public void invalidateActiveLinksFor(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        for (LinkRecord previous : linkRecordRepository.findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(userId)) {
            previous.setInvalidatedAt(now);
            linkRecordRepository.save(previous);
        }
    }

    private void finishIfNotPending(LinkRecord record, LinkOutcome outcome) {
        if (outcome.isPending()) {
            return;
        }
        record.setUsedAt(LocalDateTime.now());
        linkRecordRepository.save(record);
        linkSessionService.establish(outcome.userId(), record.getToken());
    }

    private LinkRecord findValidRecord(String token) {
        LinkRecord record = linkRecordRepository.findByToken(token)
                .orElseThrow(() -> new LoginLinkInvalidException("Link invalid or expired"));
        if (record.getInvalidatedAt() != null || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }
        return record;
    }

    private LinkPayload toPayload(LinkRecord record) {
        return new LinkPayload(record.getUserId(), record.getEmail(), readExtra(record.getExtraJson()));
    }

    private String writeExtra(Map<String, String> extra) {
        return objectMapper.writeValueAsString(extra == null ? Map.of() : extra);
    }

    private Map<String, String> readExtra(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
        });
    }
}
