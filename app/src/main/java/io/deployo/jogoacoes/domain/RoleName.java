package io.deployo.jogoacoes.domain;

/**
 * Symbolic names for the fixed rows seeded into {@code role} by V5__seed_roles.sql. ROLE
 * stays a table (not a Java enum) so new roles can be added later without a code change —
 * these constants just avoid magic strings for the two that exist today.
 */
public final class RoleName {

    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    public static final String PLAYER = "PLAYER";

    private RoleName() {
    }
}
