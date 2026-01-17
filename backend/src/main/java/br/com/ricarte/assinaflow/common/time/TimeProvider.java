package br.com.ricarte.assinaflow.common.time;

import java.time.Instant;
import java.time.LocalDate;

public interface TimeProvider {
    Instant now();

    /** Current date in UTC. */
    LocalDate todayUtc();
}
