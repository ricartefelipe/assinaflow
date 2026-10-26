package br.com.ricarte.assinaflow.payment;

import java.time.LocalDate;
import java.util.UUID;

public record PaymentContext(String kind, UUID subscriptionId, String plan, LocalDate start,
                             LocalDate cycle, long version, int creditCents, int priceCents) { }
