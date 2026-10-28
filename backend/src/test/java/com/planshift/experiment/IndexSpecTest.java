package com.planshift.experiment;

import com.planshift.schema.IndexNaming;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexSpecTest {

    @Test
    void defaultsToBtree() {
        IndexSpec spec = new IndexSpec(null, List.of("a", "b"));
        assertThat(spec.method()).isEqualTo("btree");
        assertThat(spec.columnsKey()).isEqualTo("a,b");
    }

    @Test
    void rejectsCompositeHash() {
        assertThatThrownBy(() -> new IndexSpec("hash", List.of("a", "b")))
                .hasMessageContaining("single column");
    }

    @Test
    void rejectsUnknownMethodDuplicatesAndTooManyColumns() {
        assertThatThrownBy(() -> new IndexSpec("gin", List.of("a"))).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> new IndexSpec("btree", List.of("a", "a"))).hasMessageContaining("only once");
        assertThatThrownBy(() -> new IndexSpec("btree", List.of("a", "b", "c", "d", "e"))).hasMessageContaining("at most");
        assertThatThrownBy(() -> new IndexSpec("btree", List.of())).hasMessageContaining("at least one");
    }

    @Test
    void namesStayDistinctPerShapeAndWithinPostgresLimit() {
        String plain = IndexNaming.managedName("t", List.of("a"), "btree");
        assertThat(IndexNaming.managedName("t", List.of("a"), "hash")).isNotEqualTo(plain);
        assertThat(IndexNaming.managedName("t", List.of("a", "b"), "btree")).isNotEqualTo(plain);
        assertThat(IndexNaming.managedName("orders", List.of("customer_id"), "btree")).isEqualTo("idx_orders_customer_id");

        String longA = IndexNaming.managedName("t", List.of("c".repeat(30), "d".repeat(30)), "btree");
        String longB = IndexNaming.managedName("t", List.of("c".repeat(30), "e".repeat(30)), "btree");
        assertThat(longA).hasSizeLessThanOrEqualTo(63).isNotEqualTo(longB);
        assertThat(IndexNaming.isManaged(longA)).isTrue();
    }
}
