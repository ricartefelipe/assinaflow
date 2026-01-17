package br.com.ricarte.assinaflow.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Centralized metrics for billing/renewal/outbox. Uses Micrometer via Spring Boot Actuator.
 *
 * Metric names are intentionally stable and low-cardinality.
 */
@Component
public class BillingMetrics {

    private final MeterRegistry registry;

    public BillingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startPaymentTimer() {
        return Timer.start(registry);
    }

    public void stopPaymentTimer(Timer.Sample sample, boolean approved) {
        sample.stop(registry.timer("payment_charge_duration"));
        registry.counter("payment_charge_total", "approved", String.valueOf(approved)).increment();
    }

    public void renewalAttempt(boolean success, String mode) {
        registry.counter("renewal_attempt_total", "success", String.valueOf(success), "mode", mode).increment();
    }

    public void subscriptionSuspended(String mode) {
        registry.counter("subscription_suspended_total", "mode", mode).increment();
    }

    public void outboxEnqueued(String eventType) {
        registry.counter("outbox_enqueued_total", "eventType", safe(eventType)).increment();
    }

    public void outboxPublish(boolean success) {
        registry.counter("outbox_publish_total", "success", String.valueOf(success)).increment();
    }

    public void outboxDeadLetter() {
        registry.counter("outbox_dead_total").increment();
    }

    private static String safe(String v) {
        return v == null ? "unknown" : v;
    }
}
