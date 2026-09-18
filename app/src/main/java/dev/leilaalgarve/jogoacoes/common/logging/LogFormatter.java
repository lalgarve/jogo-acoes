package dev.leilaalgarve.jogoacoes.common.logging;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collection;
import java.util.Map;

/**
 * Truncates a long {@code Collection}/{@code Map} before it's logged (spec 05-011): more than
 * one item keeps only the first, followed by {@code +[N]} (N = remaining count) -- e.g. a list
 * of 10 e-mails becomes {@code joao@exemplo.com+[9]}. Applied to one argument/return value at
 * a time by the three logging aspects, not recursively inside the kept first item.
 */
public final class LogFormatter {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private LogFormatter() {
    }

    public static String summarize(Object value) {
        if (value instanceof Collection<?> collection && collection.size() > 1) {
            return serialize(collection.iterator().next()) + "+[" + (collection.size() - 1) + "]";
        }
        if (value instanceof Map<?, ?> map && map.size() > 1) {
            Map.Entry<?, ?> first = map.entrySet().iterator().next();
            return serialize(first.getKey()) + "=" + serialize(first.getValue()) + "+[" + (map.size() - 1) + "]";
        }
        return serialize(value);
    }

    // A String logs as itself, not JSON-quoted -- matches the spec's own example
    // (joao@exemplo.com+[9], not "joao@exemplo.com"+[9]) and reads better in a DEBUG line than
    // an escaped-quotes JSON string would.
    private static String serialize(Object value) {
        if (value instanceof String string) {
            return string;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (RuntimeException e) {
            // e.g. a cyclic JPA relationship -- the log line is expendable, the real call
            // this wraps is not, so fall back instead of propagating.
            return String.valueOf(value);
        }
    }
}
