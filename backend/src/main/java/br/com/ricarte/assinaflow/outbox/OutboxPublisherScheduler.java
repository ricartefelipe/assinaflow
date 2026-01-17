package br.com.ricarte.assinaflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.payments.async.enabled", havingValue = "true")
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxPublisher outboxPublisher;
    private final int batchSize;

    public OutboxPublisherScheduler(OutboxPublisher outboxPublisher, @Value("${app.outbox.publisher.batchSize:100}") int batchSize) {
        this.outboxPublisher = outboxPublisher;
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(cron = "${app.outbox.publisher.cron:*/2 * * * * *}", zone = "UTC")
    public void runPublisher() {
        int published = outboxPublisher.publishPending(batchSize);
        if (published > 0) {
            log.info("outbox published={}", published);
        }
    }
}
