package br.com.ricarte.assinaflow.outbox;

public enum OutboxStatus {
    /** Pending publication to the broker. */
    PENDING,
    /** Successfully published (at-least-once). */
    SENT,
    /** Giving up after too many publish attempts (manual intervention required). */
    DEAD
}
