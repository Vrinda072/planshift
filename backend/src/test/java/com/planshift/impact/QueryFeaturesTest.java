package com.planshift.impact;

import com.planshift.experiment.IndexSpec;
import com.planshift.querybuilder.QuerySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryFeaturesTest {

    private static QuerySpec query(String filter, String operator, String orderBy, String aggregate) {
        return new QuerySpec("t", null, aggregate, null, null, filter, operator, "1", orderBy, null, 100);
    }

    private static IndexSpec index(String... columns) {
        return new IndexSpec(List.of(columns));
    }

    @Test
    void columnOrderDecidesWhetherACompositeIndexCanServeTheFilter() {
        QuerySpec query = query("lat", "=", null, null);
        assertThat(QueryFeatures.accessOf(query, index("lat", "base"))).isEqualTo(QueryFeatures.Access.FILTER);
        assertThat(QueryFeatures.accessOf(query, index("base", "lat"))).isEqualTo(QueryFeatures.Access.NONE);
    }

    @Test
    void leadingOrderColumnCountsOnlyWithoutAnAggregate() {
        assertThat(QueryFeatures.accessOf(query("a", "=", "b", null), index("b"))).isEqualTo(QueryFeatures.Access.ORDER);
        assertThat(QueryFeatures.accessOf(query("a", "=", "b", "COUNT"), index("b"))).isEqualTo(QueryFeatures.Access.NONE);
    }

    @Test
    void notEqualsAndLikeAreNotTreatedAsIndexable() {
        assertThat(QueryFeatures.accessOf(query("a", "!=", null, null), index("a"))).isEqualTo(QueryFeatures.Access.NONE);
        assertThat(QueryFeatures.accessOf(query("a", "LIKE", null, null), index("a"))).isEqualTo(QueryFeatures.Access.NONE);
    }

    @Test
    void orderIsCoveredOnlyAfterAnEqualityOnTheLeadingColumn() {
        assertThat(QueryFeatures.coversOrder(query("a", "=", "b", null), index("a", "b"))).isTrue();
        assertThat(QueryFeatures.coversOrder(query("a", ">", "b", null), index("a", "b"))).isFalse();
        assertThat(QueryFeatures.coversOrder(query("a", "=", "b", null), index("a"))).isFalse();
    }
}
