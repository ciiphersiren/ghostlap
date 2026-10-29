package com.ghostlap.race.lap;

import com.ghostlap.race.IntegrationTest;
import com.ghostlap.race.TestLaps;
import com.ghostlap.race.TestTokens;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Submitting laps moves the custom metrics, and they show up at /actuator/prometheus. */
class LapMetricsIntegrationTest extends IntegrationTest {

    @Autowired
    private MeterRegistry registry;

    private final String token = TestTokens.forPlayer(1, "reet");

    // The registry lives as long as the shared test context, so compare before/after.
    private double submitted() {
        return registry.get("ghostlap.laps.submitted").counter().count();
    }

    private double rejected(RejectReason reason) {
        return registry.get("ghostlap.laps.rejected").tag("reason", reason.name()).counter().count();
    }

    private long validations() {
        return registry.get("ghostlap.lap.validation").timer().count();
    }

    @Test
    void acceptedLapCountsAsSubmittedOnly() throws Exception {
        double submittedBefore = submitted();
        double tooFastBefore = rejected(RejectReason.TOO_FAST);
        long validationsBefore = validations();

        submit(token, TestLaps.validLap(16_000)).andExpect(status().isCreated());

        assertThat(submitted()).isEqualTo(submittedBefore + 1);
        assertThat(rejected(RejectReason.TOO_FAST)).isEqualTo(tooFastBefore);
        assertThat(validations()).isEqualTo(validationsBefore + 1);
    }

    @Test
    void rejectedLapCountsUnderItsReason() throws Exception {
        double submittedBefore = submitted();
        double tooFastBefore = rejected(RejectReason.TOO_FAST);

        submit(token, TestLaps.validLap(5_000)).andExpect(status().isCreated());

        assertThat(submitted()).isEqualTo(submittedBefore + 1);
        assertThat(rejected(RejectReason.TOO_FAST)).isEqualTo(tooFastBefore + 1);
    }

    @Test
    void metricsArePublishedForPrometheus() throws Exception {
        submit(token, TestLaps.validLap(16_000));

        String body = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .contains("ghostlap_laps_submitted_total")
                .contains("ghostlap_laps_rejected_total{reason=\"SPEED_EXCEEDED\"}")
                .contains("ghostlap_lap_validation_seconds_bucket")
                .contains("http_server_requests_seconds_bucket"); // histogram for p95 latency
    }
}
