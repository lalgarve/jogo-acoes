package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.email.OutgoingEmail;

import java.util.HashMap;
import java.util.Map;

/**
 * The e-mails a competition sends (spec 05-061): an invitation or a registration link to someone
 * without an account yet, or a login link to a registered player, worded after how they got there
 * (an admin's invitation or their own entry request). The templates are in
 * {@link CompetitionEmailTemplates}.
 */
record CompetitionEmail(Long userId, String recipient, String name, String competitionName, String link,
                        String templateName) implements OutgoingEmail {

    /** An admin invited someone who has no account yet. */
    static CompetitionEmail invite(Long userId, String recipient, String competitionName, String link) {
        return new CompetitionEmail(userId, recipient, null, competitionName, link, "invite");
    }

    /** Someone with no registered account asked to enter; they finish the registration first. */
    static CompetitionEmail registrationLink(Long userId, String recipient, String competitionName, String link) {
        return new CompetitionEmail(userId, recipient, null, competitionName, link, "registration-link");
    }

    /** A registered player, invited by an admin or entering on their own request. */
    static CompetitionEmail loginLink(Long userId, String recipient, String name, String competitionName,
                                      RequestType origin, String link) {
        String template = origin == RequestType.INVITE ? "login-link-invite" : "login-link-request";
        return new CompetitionEmail(userId, recipient, name, competitionName, link, template);
    }

    @Override
    public Map<String, Object> templateData() {
        Map<String, Object> data = new HashMap<>();
        putIfPresent(data, "name", name);
        putIfPresent(data, "competitionName", competitionName);
        putIfPresent(data, "link", link);
        return data;
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null) {
            data.put(key, value);
        }
    }
}
