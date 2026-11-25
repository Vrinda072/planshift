package com.planshift.experiment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Tidies up after a restart: the queue lives in memory, so anything still marked queued or running will never
 * finish, and a run that died mid-way may still own an index. Runs while the context is starting, before the web
 * server accepts requests, so it can't race a newly submitted experiment.
 */
@Component
public class ExperimentRecovery {

    private static final Logger log = LoggerFactory.getLogger(ExperimentRecovery.class);

    private final ExperimentRepository experimentRepository;
    private final IndexChangeService indexChangeService;

    public ExperimentRecovery(ExperimentRepository experimentRepository, IndexChangeService indexChangeService) {
        this.experimentRepository = experimentRepository;
        this.indexChangeService = indexChangeService;
    }

    @PostConstruct
    public void recover() {
        int interrupted = experimentRepository.failUnfinished();
        if (interrupted > 0) {
            log.warn("Marked {} interrupted experiment(s) as failed", interrupted);
        }
        indexChangeService.dropRecordedIndexes();
        indexChangeService.dropLegacyIndexes();
    }
}
