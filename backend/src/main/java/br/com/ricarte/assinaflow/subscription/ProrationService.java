package br.com.ricarte.assinaflow.subscription;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ProrationService {

    /**
     * Positive = amount to charge now (upgrade). Negative = credit for next renewal (downgrade).
     */
    public int proratedDeltaCents(Plan from, Plan to, LocalDate startDate, LocalDate expirationDate, LocalDate today) {
        long cycleDays = Math.max(1, ChronoUnit.DAYS.between(startDate, expirationDate));
        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(today, expirationDate));
        int unusedFrom = (int) ((long) from.getPriceCents() * remainingDays / cycleDays);
        int unusedTo = (int) ((long) to.getPriceCents() * remainingDays / cycleDays);
        return unusedTo - unusedFrom;
    }

    public int renewalAmountCents(Plan plan, int renewalCreditCents) {
        return Math.max(0, plan.getPriceCents() - Math.max(0, renewalCreditCents));
    }
}
