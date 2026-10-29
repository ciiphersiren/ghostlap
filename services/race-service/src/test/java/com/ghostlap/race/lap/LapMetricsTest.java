package com.ghostlap.race.lap;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit test with an in-memory registry: no Spring, no Prometheus. */
class LapMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final LapMetrics metrics = new LapMetrics(registry);

    private double rejected(RejectReason reason) {
        return registry.get("ghostlap.laps.rejected").tag("reason", reason.name()).counter().count();
    }

    @Test
    void everyReasonStartsAtZero() {
        for (RejectReason reason : RejectReason.values()) {
            assertThat(rejected(reason)).isZero();
        }
        assertThat(registry.get("ghostlap.laps.submitted").counter().count()).isZero();
    }

    @Test
    void countsSubmittedAndRejectedPerReason() {
        metrics.lapSubmitted();
        metrics.lapSubmitted();
        metrics.lapRejected(RejectReason.TOO_FAST);

        assertThat(registry.get("ghostlap.laps.submitted").counter().count()).isEqualTo(2);
        assertThat(rejected(RejectReason.TOO_FAST)).isEqualTo(1);
        assertThat(rejected(RejectReason.SPEED_EXCEEDED)).isZero();
    }

    @Test
    void timesValidationAndReturnsItsResult() {
        String result = metrics.timeValidation(() -> "validated");

        assertThat(result).isEqualTo("validated");
        assertThat(registry.get("ghostlap.lap.validation").timer().count()).isEqualTo(1);
    }
}
