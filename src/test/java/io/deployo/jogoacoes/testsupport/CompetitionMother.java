package io.deployo.jogoacoes.testsupport;

import io.deployo.jogoacoes.api.model.CompetitionCreateRequest;
import io.deployo.jogoacoes.api.model.CompetitionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Object Mother + Test Data Builder combo (see docs/desenvolvimento.md): each factory
 * returns a request body already filled with valid data. Scenarios that test one invalid
 * field at a time (create_competition.feature's "Administrator tries to create a
 * competition with invalid data") start from here and overwrite just that field, keeping
 * the rest valid -- the generated CompetitionCreateRequest DTO already exposes fluent
 * setters, so it doubles as the builder.
 */
public final class CompetitionMother {

    private CompetitionMother() {
    }

    public static CompetitionCreateRequest validPublicCompetition() {
        return new CompetitionCreateRequest()
                .name("Public Competition " + UUID.randomUUID())
                .type(CompetitionType.PUBLIC)
                .startDate(LocalDate.now().plusDays(7))
                .durationDays(30)
                .recurring(false)
                .buyFee(0.5)
                .sellFee(0.5);
    }

    public static CompetitionCreateRequest validPrivateCompetition() {
        return validPublicCompetition()
                .type(CompetitionType.PRIVATE)
                .emails(inviteeEmails());
    }

    private static List<String> inviteeEmails() {
        List<String> emails = new ArrayList<>();
        emails.add("invitee1-" + UUID.randomUUID() + "@example.com");
        emails.add("invitee2-" + UUID.randomUUID() + "@example.com");
        return emails;
    }
}
