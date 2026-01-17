package br.com.ricarte.assinaflow.testutil;

import br.com.ricarte.assinaflow.common.time.TimeProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public class MutableTimeProvider implements TimeProvider {

    private final AtomicReference<Instant> nowRef = new AtomicReference<>(Instant.parse("2025-01-01T00:00:00Z"));

    public void setNow(Instant now) {
        nowRef.set(now);
    }

    @Override
    public Instant now() {
        return nowRef.get();
    }

    @Override
    public LocalDate todayUtc() {
        return LocalDate.ofInstant(now(), ZoneOffset.UTC);
    }
}
