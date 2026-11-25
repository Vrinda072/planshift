package com.planshift.experiment;

import com.planshift.lock.DatabaseBusyException;
import com.planshift.lock.DatabaseLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;


/**
 * Creates (and removes) a lasting index from a finished experiment. Off unless
 * {@code planshift.keep-index.enabled} is set: the API has no sign-in, so on a shared deployment this would let any
 * visitor change the database everyone else measures against.
 */
@Service
public class KeptIndexService {

    private final ExperimentRepository experimentRepository;
    private final IndexChangeService indexChangeService;
    private final DatabaseLock databaseLock;
    private final boolean enabled;

    public KeptIndexService(ExperimentRepository experimentRepository, IndexChangeService indexChangeService,
                            DatabaseLock databaseLock,
                            @Value("${planshift.keep-index.enabled:false}") boolean enabled) {
        this.experimentRepository = experimentRepository;
        this.indexChangeService = indexChangeService;
        this.databaseLock = databaseLock;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public Experiment keep(long experimentId) {
        requireEnabled();
        try (DatabaseLock.Held ignored = databaseLock.tryAcquire().orElseThrow(DatabaseBusyException::new)) {
            Experiment experiment = find(experimentId);
            if (experiment.keptIndexName() != null) {
                return experiment;
            }
            if (experiment.status() != ExperimentStatus.COMPLETED || experiment.targetColumn() == null
                    || experiment.targetTable() == null) {
                throw new IllegalArgumentException("Only a completed experiment that tested an index can keep it");
            }
            experimentRepository.findKeeping(experiment.targetTable(), experiment.targetColumn()).ifPresent(other -> {
                throw new IllegalArgumentException("Experiment " + other + " already keeps this index");
            });
            indexChangeService.createKeptIndex(experimentId, experiment.targetTable(),
                    new IndexSpec(java.util.List.of(experiment.targetColumn().split(","))));
            return find(experimentId);
        }
    }

    public Experiment remove(long experimentId) {
        requireEnabled();
        try (DatabaseLock.Held ignored = databaseLock.tryAcquire().orElseThrow(DatabaseBusyException::new)) {
            Experiment experiment = find(experimentId);
            if (experiment.keptIndexName() != null) {
                indexChangeService.dropKeptIndex(experimentId, experiment.keptIndexName());
            }
            return find(experimentId);
        }
    }

    private Experiment find(long experimentId) {
        return experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No experiment with id " + experimentId));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new KeepIndexDisabledException();
        }
    }
}
