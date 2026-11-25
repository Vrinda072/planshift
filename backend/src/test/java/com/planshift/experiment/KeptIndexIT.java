package com.planshift.experiment;

import com.planshift.lock.DatabaseBusyException;
import com.planshift.lock.DatabaseLock;
import com.planshift.workload.QueryCategory;
import com.planshift.workload.WorkloadQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "planshift.keep-index.enabled=true")
@Testcontainers
class KeptIndexIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private IndexExperimentService experimentService;

    @Autowired
    private ExperimentRepository experimentRepository;

    @Autowired
    private ExperimentRecovery recovery;

    @Autowired
    private KeptIndexService keptIndexService;

    @Autowired
    private DatabaseLock databaseLock;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void aKeptIndexSurvivesCleanupAndCanBeRemovedAgain() throws Exception {
        jdbc.execute("CREATE TABLE keep_probe (a INT)");
        jdbc.execute("INSERT INTO keep_probe SELECT generate_series(1, 2000)");
        long first = finishedExperiment();
        long second = finishedExperiment();

        try (DatabaseLock.Held ignored = databaseLock.acquire()) {
            assertThatThrownBy(() -> keptIndexService.keep(first)).isInstanceOf(DatabaseBusyException.class);
        }
        String kept = keptIndexService.keep(first).keptIndexName();
        assertThat(keptIndexService.keep(first).keptIndexName()).isEqualTo(kept);
        // The same columns can't be kept twice under another name.
        assertThatThrownBy(() -> keptIndexService.keep(second)).isInstanceOf(IllegalArgumentException.class);

        recovery.recover();
        finishedExperiment();
        assertThat(indexes()).containsExactly(kept);

        assertThatThrownBy(() -> keptIndexService.keep(999_999)).hasMessageContaining("404");
        assertThat(keptIndexService.remove(first).keptIndexName()).isNull();
        assertThat(indexes()).isEmpty();
    }

    private long finishedExperiment() throws InterruptedException {
        WorkloadQuery query = new WorkloadQuery("KEEP", "keep probe", QueryCategory.values()[0], "probe",
                "SELECT * FROM keep_probe WHERE a = 7");
        long id = experimentService.startAsync(List.of(query), Map.of(), 1, 0.2, "keep_probe",
                IndexSpec.of("a"), "CUSTOM_QUERY");
        for (int i = 0; i < 600; i++) {
            if (experimentRepository.findById(id).orElseThrow().status() == ExperimentStatus.COMPLETED) {
                return id;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Experiment " + id + " did not complete");
    }

    private List<String> indexes() {
        return jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'keep_probe'", String.class);
    }
}
