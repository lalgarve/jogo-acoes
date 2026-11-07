package dev.leilaalgarve.jogoacoes.competition;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Spec 05-057: a competition has at most one participation per e-mail, compared ignoring case
 * (unique index {@code participation_competition_email_uidx}). The checks both invite paths make
 * before creating anything, so the whole list is rejected up front.
 */
final class InviteEmails {

    private InviteEmails() {
    }

    /** The e-mails that appear more than once in {@code emails}, in lower case, in list order. */
    static List<String> repeated(Collection<String> emails) {
        Set<String> seen = new HashSet<>();
        Set<String> repeated = new LinkedHashSet<>();
        for (String email : emails) {
            String key = normalized(email);
            if (!seen.add(key)) {
                repeated.add(key);
            }
        }
        return List.copyOf(repeated);
    }

    static List<String> normalized(Collection<String> emails) {
        return emails.stream().map(InviteEmails::normalized).toList();
    }

    static String repeatedMessage(List<String> repeated) {
        return "Repeated e-mail(s) in the list: " + String.join(", ", repeated);
    }

    static String alreadyInMessage(List<String> alreadyIn) {
        return "E-mail(s) already in this competition: " + String.join(", ", alreadyIn);
    }

    private static String normalized(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
