package io.deployo.jogoacoes.domain;

/**
 * The catalog of auditable events, decided in Iteração 3 (see docs/iteracao-3.md). Each
 * constant carries its own description and the table {@code related_object_id} refers to,
 * since {@code log_type} is what tells a reader which table that polymorphic reference
 * points to. PARTICIPATION_STATUS_CHANGED also covers a participation's removal: there is
 * no ParticipationStatus for "removed" (it's a hard delete, not a status), but the message
 * says so, and related_object_id having no FK constraint (by design) lets the log entry
 * outlive the row it refers to.
 */
public enum LogType {

    COMPETITION_CREATED("Competition created", "competition"),
    PARTICIPATION_STATUS_CHANGED("Participation status changed", "participation"),
    LOGIN_LINK_ISSUED("Login link issued", "login_link");

    private final String description;
    private final String relatedTable;

    LogType(String description, String relatedTable) {
        this.description = description;
        this.relatedTable = relatedTable;
    }

    public String getDescription() {
        return description;
    }

    public String getRelatedTable() {
        return relatedTable;
    }
}
