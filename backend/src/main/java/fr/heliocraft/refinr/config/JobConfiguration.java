// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.out.jobs.InMemoryJobStore;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.JobStorePort;
import fr.heliocraft.refinr.application.service.AnalysisJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;

import java.time.Clock;
import java.time.Duration;

/**
 * Jobs d'analyse ({@code /api/v1/jobs/...}) : stockage en memoire et execution bornee (proprietes
 * {@code refinr.jobs.*}). Arret progressif a la fermeture du contexte (voir {@link AnalysisJobService#close()}).
 */
@Configuration
public class JobConfiguration {

    static final int DEFAULT_MAX_CONCURRENT = 2;
    static final int DEFAULT_MAX_QUEUED = 20;
    static final String DEFAULT_MAX_DURATION = "30m";
    static final String DEFAULT_RETENTION = "24h";
    static final int DEFAULT_MAX_RETAINED = 100;
    static final String DEFAULT_SHUTDOWN_GRACE = "30s";

    private static final Logger log = LoggerFactory.getLogger(JobConfiguration.class);
    private static final Clock CLOCK = Clock.systemUTC();

    private final int maxConcurrent;
    private final int maxQueued;
    private final int maxRetained;

    public JobConfiguration(
            @Value("${refinr.jobs.max-concurrent:" + DEFAULT_MAX_CONCURRENT + "}") int maxConcurrent,
            @Value("${refinr.jobs.max-queued:" + DEFAULT_MAX_QUEUED + "}") int maxQueued,
            @Value("${refinr.jobs.max-retained:" + DEFAULT_MAX_RETAINED + "}") int maxRetained) {
        // Un job actif n'est jamais evince : le stockage doit pouvoir garder tous les jobs actifs a la fois
        if (maxRetained < maxConcurrent + maxQueued) {
            throw new IllegalStateException("refinr.jobs.max-retained (" + maxRetained + ") must be at least "
                    + "refinr.jobs.max-concurrent + refinr.jobs.max-queued (" + (maxConcurrent + maxQueued) + ")");
        }
        this.maxConcurrent = maxConcurrent;
        this.maxQueued = maxQueued;
        this.maxRetained = maxRetained;
    }

    @Bean
    JobStorePort<JobView> jobStore(@Value("${refinr.jobs.retention:" + DEFAULT_RETENTION + "}") Duration retention) {
        return new InMemoryJobStore<>(maxRetained, retention, CLOCK);
    }

    @Bean(destroyMethod = "close")
    AnalysisJobService analysisJobs(PreviewBacklog previewBacklog, AnalyzeDocument analyzeDocument,
                                    DocumentSourcePort documentSource, JobStorePort<JobView> jobStore,
                                    @Value("${" + UseCaseConfig.MAX_CONTENT_CHARS_PROPERTY + ":"
                                            + UseCaseConfig.DEFAULT_MAX_CONTENT_CHARS + "}") int maxContentChars,
                                    @Value("${refinr.jobs.max-duration:" + DEFAULT_MAX_DURATION + "}") Duration maxDuration,
                                    @Value("${refinr.jobs.shutdown-grace:" + DEFAULT_SHUTDOWN_GRACE + "}") Duration shutdownGrace) {
        AnalysisJobService.Settings settings = new AnalysisJobService.Settings(maxConcurrent, maxQueued, maxDuration,
                shutdownGrace);
        log.info("Analysis jobs: {} at a time, {} waiting at most, {} maximum duration, {} kept at most",
                maxConcurrent, maxQueued, maxDuration, maxRetained);
        return new AnalysisJobService(previewBacklog, analyzeDocument, documentSource, maxContentChars, jobStore,
                settings, CLOCK);
    }

    /**
     * Arret des jobs des le debut de la fermeture du contexte : l'evenement est publie avant l'arret progressif du
     * serveur web ({@code server.shutdown=graceful}, au plus {@code spring.lifecycle.timeout-per-shutdown-phase} pour
     * les requetes en cours). Les deux delais courent donc en meme temps ; {@code close()} attend ensuite le reste de
     * {@code refinr.jobs.shutdown-grace}. Le conteneur doit laisser plus que le plus long des deux (stop_grace_period).
     */
    @Bean
    ApplicationListener<ContextClosedEvent> analysisJobsShutdown(AnalysisJobService analysisJobs) {
        return event -> analysisJobs.beginShutdown();
    }
}
