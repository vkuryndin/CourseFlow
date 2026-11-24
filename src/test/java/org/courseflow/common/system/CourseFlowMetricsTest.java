package org.courseflow.common.system;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseFlowMetricsTest {
    @Test
    void eachBusinessEventIncrementsItsOwnCounter() {
        var registry = new SimpleMeterRegistry();
        var metrics = new CourseFlowMetrics(registry);
        metrics.rateLimited();
        metrics.rateLimited();
        metrics.resetRequested();
        metrics.resetAccepted();
        metrics.resetRejected();
        metrics.resetProcessingFailed();
        metrics.resetMailSent();
        metrics.resetMailFailed();
        assertEquals(2.0, registry.get("courseflow.auth.rate_limited").counter().count());
        for (String suffix : new String[]{"requests", "accepted", "rejected", "processing_failed", "mail_sent",
                "mail_failed"}) {
            assertEquals(1.0, registry.get("courseflow.password.reset." + suffix).counter().count());
        }
        assertEquals(7, registry.getMeters().size());
    }
}
