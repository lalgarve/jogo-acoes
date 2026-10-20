package dev.leilaalgarve.jogoacoes.emailservice.send;

/** Where a send is (spec 05-046). */
public enum EmailSendStatus {
    /** Accepted and recorded; waiting for the publisher to put it on the send queue. */
    PENDING,
    /** On the send queue. email-lambda delivers it; a delivery error brings it back as PENDING or FAILED. */
    QUEUED,
    /** Given up on: SES rejected it, or it did not get through within the give-up time. */
    FAILED
}
