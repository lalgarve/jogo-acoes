package dev.leilaalgarve.jogoacoes.link.dto;

import java.util.Map;

/**
 * The only type a {@code LinkHandler} sees: never a subclass hierarchy, so `link` never needs
 * to know or downcast to a concrete implementation's own shape. {@code extra} carries whatever
 * an implementation needs beyond {@code userId}/{@code email} (e.g. a participation id) — it is
 * opaque to the rest of the `link` module.
 */
public record LinkPayload(Long userId, String email, Map<String, String> extra) {
}
