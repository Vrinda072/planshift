package com.planshift.dataimport;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real Postgres COPY-based import path against a throwaway
 * container -- not a mock -- since the whole point of this feature is that
 * Postgres itself parses and loads the CSV.
 */
@SpringBootTest
@Testcontainers
class CsvImportServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private CsvImportService csvImportService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void importsCsvWithInferredTypes() throws Exception {
        String csv = "movie_id,title,release_year,rating\n"
                + "1,Arrival,2016,8.0\n"
                + "2,\"Blade Runner, 2049\",2017,8.1\n"
                + "3,Dune,2021,8.0\n";
        MockMultipartFile file = new MockMultipartFile("file", "movies.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportResult result = csvImportService.importCsv("kaggle movies!", file);

        assertThat(result.tableName()).isEqualTo("kaggle_movies_");
        assertThat(result.rowCount()).isEqualTo(3);
        assertThat(result.columnNames()).containsExactly("movie_id", "title", "release_year", "rating");

        // movie_id and release_year should have been inferred as integers, rating as a float
        Long movieId = jdbc.queryForObject("SELECT movie_id FROM kaggle_movies_ WHERE title = 'Dune'", Long.class);
        assertThat(movieId).isEqualTo(3L);

        // the embedded comma inside quotes must not have split the column
        String comma = jdbc.queryForObject(
                "SELECT title FROM kaggle_movies_ WHERE movie_id = 2", String.class);
        assertThat(comma).isEqualTo("Blade Runner, 2049");
    }

    @Test
    void refusesToOverwriteABuiltInTable() {
        MockMultipartFile file = new MockMultipartFile("file", "x.csv", "text/csv",
                "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> csvImportService.importCsv("orders", file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reimportingReplacesThePreviousTable() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "x.csv", "text/csv",
                "a,b\n1,2\n3,4\n".getBytes(StandardCharsets.UTF_8));
        csvImportService.importCsv("replaceable", first);

        MockMultipartFile second = new MockMultipartFile("file", "x.csv", "text/csv",
                "a,b\n9,9\n".getBytes(StandardCharsets.UTF_8));
        ImportResult result = csvImportService.importCsv("replaceable", second);

        assertThat(result.rowCount()).isEqualTo(1);
    }
}
