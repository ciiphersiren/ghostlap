package com.ghostlap.race.lap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Custom Micrometer metrics for laps. Micrometer names use dots; the Prometheus registry turns them
 * into underscores and adds unit suffixes, so at /actuator/prometheus they appear as:
 * <ul>
 *   <li>{@code ghostlap_laps_submitted_total} - every lap received</li>
 *   <li>{@code ghostlap_laps_rejected_total{reason="TOO_FAST"}} - one series per reject reason</li>
 *   <li>{@code ghostlap_lap_validation_seconds_*} - how long LapValidator takes (count, sum, max, buckets)</li>
 * </ul>
 */
@Component
public class LapMetrics {

    private final Counter submitted;
    private final Map<RejectReason, Counter> rejected = new EnumMap<>(RejectReason.class);
    private final Timer validation;

    public LapMetrics(MeterRegistry registry) {
        submitted = Counter.builder("ghostlap.laps.submitted")
                .description("Laps received by race-service")
                .register(registry);
        // Register every reason up front, so each series exists (at 0) before the first cheat.
        for (RejectReason reason : RejectReason.values()) {
            rejected.put(reason, Counter.builder("ghostlap.laps.rejected")
                    .description("Laps rejected by validation")
                    .tag("reason", reason.name())
                    .register(registry));
        }
        validation = Timer.builder("ghostlap.lap.validation")
                .description("Time spent validating a lap (decoding the ghost and running the rules)")
                .publishPercentileHistogram() // buckets, so Prometheus can compute p95
                .register(registry);
    }

    public void lapSubmitted() {
        submitted.increment();
    }

    public void lapRejected(RejectReason reason) {
        rejected.get(reason).increment();
    }

    /** Runs the validation and records how long it took. */
    public <T> T timeValidation(Supplier<T> validationStep) {
        return validation.record(validationStep);
    }
}
