package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a recipient's domain is a disposable e-mail domain (spec 05-048): the domain itself or
 * any domain above it ({@code x.mailinator.com} → {@code mailinator.com}) is in the imported lists
 * of any repository. Never the top-level domain alone. One indexed query.
 */
@Service
public class DisposableDomainChecker {

    private final JdbcTemplate jdbcTemplate;

    public DisposableDomainChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** @param domain lower case, ASCII form */
    public boolean isDisposable(String domain) {
        List<String> candidates = new ArrayList<>();
        for (String candidate = domain; candidate.contains("."); candidate = candidate.substring(candidate.indexOf('.') + 1)) {
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            return false;
        }
        String placeholders = String.join(", ", candidates.stream().map(c -> "?").toList());
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM disposable_domain WHERE domain IN (" + placeholders + "))",
                Boolean.class, candidates.toArray()));
    }
}
