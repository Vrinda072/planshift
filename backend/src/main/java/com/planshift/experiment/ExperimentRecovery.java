package com.planshift.experiment;

import com.planshift.lock.DatabaseLock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tidies up after instances that stopped part way. The queue lives in memory, so their queued and running
 * experiments will never finish, and one that died mid-run may still own an index. Other instances may be alive
 * (a rolling deploy runs old and new side by side), so nothing of theirs is touched unless it is provably
 * abandoned:
 * <ul>
 * <li>a RUNNING experiment is only ever worked on under the database lock, so whoever holds the lock knows that
 * every row still marked RUNNING is orphaned, and that no recorded index is in use;</li>
 * <li>a QUEUED one is abandoned once its owner has stopped sending heartbeats.</li>
 * </ul>
 * While there is nothing to look after, no queries are made, so an idle serverless database can suspend.
 */
@Component
public class ExperimentRecovery {

    private static final Logger log = LoggerFactory.getLogger(ExperimentRecovery.class);

    private final ExperimentRepository experimentRepository;
    private final IndexChangeService indexChangeService;
    private final DatabaseLock databaseLock;
    private final ExperimentOwner owner;
    private final int staleAfterSeconds;
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("experiment-upkeep").daemon().factory());
    private volatile boolean followUpNeeded;

    public ExperimentRecovery(ExperimentRepository experimentRepository, IndexChangeService indexChangeService,
                              DatabaseLock databaseLock, ExperimentOwner owner,
                              @Value("${planshift.experiments.stale-after-seconds:60}") int staleAfterSeconds) {
        this.experimentRepository = experimentRepository;
        this.indexChangeService = indexChangeService;
        this.databaseLock = databaseLock;
        this.owner = owner;
        this.staleAfterSeconds = staleAfterSeconds;
    }

    /** Runs while the context is starting, before the web server accepts requests. */
    @PostConstruct
    void start() {
        recover();
        long period = Math.max(1, staleAfterSeconds / 6);
        ticker.scheduleWithFixedDelay(this::tick, period, period, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        ticker.shutdownNow();
    }

    public void recover() {
        Optional<DatabaseLock.Held> held = databaseLock.tryAcquire();
        if (held.isPresent()) {
            try (DatabaseLock.Held ignored = held.get()) {
                recoverHoldingLock();
            }
        } else {
            log.info("Another instance is using the database; leaving its running experiment alone");
            failAbandonedQueued();
        }
        followUpNeeded = held.isEmpty() || experimentRepository.countUnfinishedOwnedByOthers(owner.id()) > 0;
    }

    /** Only call while holding the database lock. */
    void recoverHoldingLock() {
        int orphaned = experimentRepository.failRunning();
        if (orphaned > 0) {
            log.warn("Marked {} interrupted experiment(s) as failed", orphaned);
        }
        failAbandonedQueued();
        indexChangeService.dropRecordedIndexes();
        indexChangeService.dropLegacyIndexes();
    }

    private void failAbandonedQueued() {
        int abandoned = experimentRepository.failAbandonedQueued(owner.id(), staleAfterSeconds);
        if (abandoned > 0) {
            log.warn("Marked {} abandoned queued experiment(s) as failed", abandoned);
        }
    }

    private void tick() {
        try {
            if (owner.hasWork()) {
                experimentRepository.heartbeat(owner.id());
            }
            if (followUpNeeded) {
                recover();
            }
        } catch (RuntimeException e) {
            log.warn("Experiment upkeep failed; it will be tried again", e);
        }
    }
}
