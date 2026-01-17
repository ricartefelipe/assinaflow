package br.com.ricarte.assinaflow.metrics;

import br.com.ricarte.assinaflow.outbox.OutboxRepository;
import br.com.ricarte.assinaflow.outbox.OutboxStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Gauges that require lightweight DB reads. In a high-throughput system, you would
 * typically materialize these counts (or sample) to avoid DB load on frequent scrapes.
 */
@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
        Gauge.builder("outbox_pending", outboxRepository, r -> r.countByStatus(OutboxStatus.PENDING))
                .description("Number of pending outbox events")
                .register(registry);

        Gauge.builder("outbox_dead", outboxRepository, r -> r.countByStatus(OutboxStatus.DEAD))
                .description("Number of dead-letter outbox events")
                .register(registry);
    }
}
