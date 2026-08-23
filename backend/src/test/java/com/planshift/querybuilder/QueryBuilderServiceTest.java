package com.planshift.querybuilder;

import com.planshift.schema.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryBuilderServiceTest {

    private SchemaService schemaService;
    private QueryBuilderService service;

    @BeforeEach
    void setUp() {
        schemaService = mock(SchemaService.class);
        service = new QueryBuilderService(schemaService);

        when(schemaService.tableExists("orders")).thenReturn(true);
        when(schemaService.columnExists(eq("orders"), any())).thenReturn(false);
        when(schemaService.columnExists("orders", "customer_id")).thenReturn(true);
        when(schemaService.columnExists("orders", "status")).thenReturn(true);
        when(schemaService.columnExists("orders", "total_amount")).thenReturn(true);
    }

    @Test
    void buildsSelectWithFilterAsParameterizedNamedQuery() {
        QuerySpec spec = new QuerySpec("orders", List.of("customer_id", "status"), null, null, null,
                "status", "=", "COMPLETED", null, null, null);

        BuiltQuery built = service.build(spec);

        assertThat(built.query().sql()).contains("SELECT \"customer_id\", \"status\" FROM \"orders\"");
        assertThat(built.query().sql()).contains("WHERE \"status\" = :filterValue");
        assertThat(built.query().sql()).doesNotContain("COMPLETED");
        assertThat(built.params()).containsEntry("filterValue", "COMPLETED");
    }

    @Test
    void rejectsUnknownTable() {
        when(schemaService.tableExists("orders")).thenReturn(false);
        QuerySpec spec = new QuerySpec("orders", List.of("customer_id"), null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.build(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownColumn() {
        QuerySpec spec = new QuerySpec("orders", List.of("nonexistent_column"), null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.build(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIdentifierThatIsNotAPlainName() {
        // A classic injection attempt via what looks like a table name -- rejected before
        // ever reaching the schema check, by the identifier pattern itself.
        QuerySpec spec = new QuerySpec("orders; DROP TABLE orders; --", List.of("customer_id"), null, null, null,
                null, null, null, null, null, null);

        assertThatThrownBy(() -> service.build(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedFilterOperator() {
        QuerySpec spec = new QuerySpec("orders", List.of("customer_id"), null, null, null,
                "status", "; DROP TABLE orders; --", "x", null, null, null);

        assertThatThrownBy(() -> service.build(spec)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void countStarDoesNotRequireAColumn() {
        QuerySpec spec = new QuerySpec("orders", null, "COUNT", null, null,
                null, null, null, null, null, null);

        BuiltQuery built = service.build(spec);

        assertThat(built.query().sql()).contains("COUNT(*) AS result");
    }

    @Test
    void groupByRequiresAggregateColumnToBeValid() {
        when(schemaService.columnExists("orders", "status")).thenReturn(true);
        QuerySpec spec = new QuerySpec("orders", null, "SUM", "total_amount", "status",
                null, null, null, null, null, null);

        BuiltQuery built = service.build(spec);

        assertThat(built.query().sql()).contains("SELECT \"status\", SUM(\"total_amount\") AS result FROM \"orders\"");
        assertThat(built.query().sql()).contains("GROUP BY \"status\"");
    }

    @Test
    void limitIsClampedToMaximum() {
        QuerySpec spec = new QuerySpec("orders", List.of("customer_id"), null, null, null,
                null, null, null, null, null, 999_999);

        BuiltQuery built = service.build(spec);

        assertThat(built.query().sql()).contains("LIMIT 5000");
    }
}
