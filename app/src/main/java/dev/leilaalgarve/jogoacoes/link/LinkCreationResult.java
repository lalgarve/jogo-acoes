package dev.leilaalgarve.jogoacoes.link;

/**
 * What {@link LinkService#create} hands back: the absolute URL to send by e-mail (spec 05-041),
 * the token in it, and the row id for audit logging.
 */
public record LinkCreationResult(Long id, String token, String url) {
}
