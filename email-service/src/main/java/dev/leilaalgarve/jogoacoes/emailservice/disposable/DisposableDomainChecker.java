package dev.leilaalgarve.jogoacoes.emailservice.disposable;

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

    private final DisposableDomainRepository disposableDomains;

    public DisposableDomainChecker(DisposableDomainRepository disposableDomains) {
        this.disposableDomains = disposableDomains;
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
        return disposableDomains.existsAnyOf(candidates);
    }
}
