package io.deployo.jogoacoes.domain;

/**
 * Which e-mail was sent. In this iteration this only labels the row in {@link SentEmail} —
 * actually rendering the template text (Thymeleaf) is a later iteration.
 */
public enum EmailTemplate {
    INVITE,
    REGISTRATION_LINK,
    LOGIN_LINK
}
