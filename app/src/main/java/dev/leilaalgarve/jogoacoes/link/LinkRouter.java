package dev.leilaalgarve.jogoacoes.link;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches by service key to whichever {@link LinkHandler} a consumer module registered as a
 * Spring bean — no central catalog of keys, consistent with `link` not knowing its consumers.
 */
@Component
public class LinkRouter {

    private final Map<String, LinkHandler> handlersByKey;

    public LinkRouter(List<LinkHandler> handlers) {
        Map<String, LinkHandler> byKey = new HashMap<>();
        for (LinkHandler handler : handlers) {
            String key = handler.key();
            if (byKey.putIfAbsent(key, handler) != null) {
                throw new IllegalStateException("Duplicate LinkHandler key: " + key);
            }
        }
        this.handlersByKey = Map.copyOf(byKey);
    }

    public LinkHandler handlerFor(String serviceKey) {
        LinkHandler handler = handlersByKey.get(serviceKey);
        if (handler == null) {
            throw new IllegalStateException("No LinkHandler registered for key: " + serviceKey);
        }
        return handler;
    }
}
