package dev.leilaalgarve.jogoacoes.link;

/** What {@link LinkService#create} hands back: the token for the URL, and the row id for audit logging. */
public record LinkCreationResult(Long id, String token) {
}
