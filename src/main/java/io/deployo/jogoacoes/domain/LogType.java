package io.deployo.jogoacoes.domain;

/**
 * Placeholder catalog — the real set of auditable events is a business decision for
 * Iteração 3. Each constant carries its own description and the table {@code
 * related_object_id} refers to, since {@code log_type} is what tells a reader which table
 * that polymorphic reference points to.
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
