package com.planshift.experiment;

import com.planshift.schema.IndexNaming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexSpecTest {

    @Test
    void joinsColumnsInOrder() {
        assertThat(new IndexSpec(List.of("a", "b")).columnsKey()).isEqualTo("a,b");
    }

    @Test
    void rejectsDuplicatesEmptyAndTooManyColumns() {
        assertThatThrownBy(() -> new IndexSpec(List.of("a", "a"))).hasMessageContaining("only once");
        assertThatThrownBy(() -> new IndexSpec(List.of("a", "b", "c", "d", "e"))).hasMessageContaining("at most");
        assertThatThrownBy(() -> new IndexSpec(List.of())).hasMessageContaining("at least one");
    }

    @Test
    void namesStayDistinctPerShapeAndWithinPostgresLimit() {
        String plain = IndexNaming.managedName("t", List.of("a"));
        assertThat(IndexNaming.managedName("t", List.of("a", "b"))).isNotEqualTo(plain);
        assertThat(IndexNaming.managedName("orders", List.of("customer_id"))).isEqualTo("idx_orders_customer_id");

        String longA = IndexNaming.managedName("t", List.of("c".repeat(30), "d".repeat(30)));
        String longB = IndexNaming.managedName("t", List.of("c".repeat(30), "e".repeat(30)));
        assertThat(longA).hasSizeLessThanOrEqualTo(63).isNotEqualTo(longB);
        assertThat(IndexNaming.isManaged(longA)).isTrue();
    }
}
