package br.com.ricarte.assinaflow.subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalScheduler.class);

    private final RenewalService renewalService;

    public SubscriptionRenewalScheduler(RenewalService renewalService) {
        this.renewalService = renewalService;
    }

    @Scheduled(cron = "${app.renewal.scheduler.cron:0 */5 * * * *}", zone = "UTC")
    public void runRenewals() {
        int processed = renewalService.processDueRenewals(100);
        if (processed > 0) {
            log.info("runRenewals processed={}", processed);
        }
    }

    @Scheduled(cron = "${app.cancellation.scheduler.cron:0 10 0 * * *}", zone = "UTC")
    public void finalizeCancellations() {
        int processed = renewalService.finalizeScheduledCancellations(200);
        if (processed > 0) {
            log.info("finalizeCancellations processed={}", processed);
        }
    }
}
