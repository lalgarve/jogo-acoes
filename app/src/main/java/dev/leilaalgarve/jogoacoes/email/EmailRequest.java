package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.email.EmailTemplate;
import dev.leilaalgarve.jogoacoes.competition.RequestType;

/**
 * Everything an {@link EmailSender} needs to record the send and pick the right one of
 * the 5 templates (docs/context/iteracao-4.md, "Catálogo de templates de e-mail"). The
 * enum alone is not enough for that last part: {@code LOGIN_LINK} covers 3 different templates
 * (login-link / login-link-invite / login-link-request), disambiguated by
 * {@code competitionName} (absent only for the standalone login case) and {@code origin}.
 *
 * @param userId           nullable — the recipient may not have an account yet.
 * @param name             nullable — only known once the recipient has a registered account.
 * @param competitionName  nullable — absent for a standalone login (no competition involved).
 * @param origin           nullable — which of the two flows led to this e-mail (admin invite vs.
 *                         a player's own request). Only consulted when {@code template} is
 *                         {@code LOGIN_LINK} and {@code competitionName} is present; ignored
 *                         otherwise, since {@code INVITE}/{@code REGISTRATION_LINK} already
 *                         imply their origin.
 */
public record EmailRequest(Long userId, String email, String name, String competitionName,
                            RequestType origin, String link, EmailTemplate template) {
}
