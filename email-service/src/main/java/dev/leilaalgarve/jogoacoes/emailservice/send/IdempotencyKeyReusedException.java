package dev.leilaalgarve.jogoacoes.emailservice.send;

/** Spec 05-059: the client already used this Idempotency-Key for a different request. */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("Idempotency-Key already used with a different request");
    }
}
