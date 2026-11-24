// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.application.port.in.AnalysisJobs.RejectedException;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.JobStorePort;
import fr.heliocraft.refinr.application.service.AnalysisJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobConfigurationTest {

    @Test
    @DisplayName("The context closing starts the job shutdown at once (before the web server's graceful phase)")
    void contextClosedStartsTheShutdown() {
        JobConfiguration config = new JobConfiguration(2, 20, 100);
        DocumentSourcePort source = mock(DocumentSourcePort.class);
        when(source.acceptsDocumentId(anyString())).thenReturn(true);
        JobStorePort<JobView> store = config.jobStore(Duration.ofHours(24));
        AnalysisJobService jobs = config.analysisJobs(mock(PreviewBacklog.class), mock(AnalyzeDocument.class), source,
                store, mock(fr.heliocraft.refinr.application.port.in.DescribeTracker.class),
                mock(org.springframework.beans.factory.ObjectProvider.class), 1000, Duration.ofMinutes(30),
                Duration.ofSeconds(30));
        try {
            config.analysisJobsShutdown(jobs).onApplicationEvent(new ContextClosedEvent(mock(ApplicationContext.class)));

            assertThrows(RejectedException.class, () -> jobs.submitPreview("123456", null));
        } finally {
            jobs.close();
        }
    }

    @Test
    @DisplayName("max-retained below max-concurrent + max-queued is refused at startup")
    void maxRetainedMustHoldEveryActiveJob() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JobConfiguration(2, 20, 21));
        assertTrue(error.getMessage().contains("refinr.jobs.max-retained"), error.getMessage());
    }
}
