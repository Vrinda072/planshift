package com.planshift.benchmark;

import com.planshift.workload.QueryCategory;
import com.planshift.workload.WorkloadQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "planshift.benchmark.timing-source=database")
@Testcontainers
class DatabaseTimingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private BenchmarkService benchmarkService;

    @Test
    void timesRunsWithPostgresOwnClockAndStillBindsParameters() {
        WorkloadQuery query = new WorkloadQuery("T", "t", QueryCategory.CUSTOM, "t",
                "SELECT g FROM generate_series(1, :upTo) g");

        RepeatedBenchmarkResult result = benchmarkService.executeRepeated(query, Map.of("upTo", 500), 3);

        assertThat(benchmarkService.timingSource()).isEqualTo(TimingSource.DATABASE);
        assertThat(result.executionTimesMs()).hasSize(3).allMatch(ms -> ms > 0);
        assertThat(result.rowsReturned()).isEqualTo(500);
    }
}
