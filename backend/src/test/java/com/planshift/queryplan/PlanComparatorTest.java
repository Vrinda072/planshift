package com.planshift.queryplan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanComparatorTest {

    private final PlanComparator comparator = new PlanComparator();

    private PlanNode leaf(String nodeType, String relation) {
        return new PlanNode(nodeType, relation, null, null, null, 0, 0, 0.0, List.of());
    }

    private PlanNode withChildren(String nodeType, PlanNode... children) {
        return new PlanNode(nodeType, null, null, null, null, 0, 0, 0.0, List.of(children));
    }

    @Test
    void identicalTreesProduceNoDiffs() {
        PlanNode a = withChildren("Sort", leaf("Seq Scan", "orders"));
        PlanNode b = withChildren("Sort", leaf("Seq Scan", "orders"));

        List<PlanDiff> diffs = comparator.compare(a, b);

        assertThat(diffs).isEmpty();
        assertThat(comparator.summarize(diffs)).isEqualTo("No change in query plan structure.");
    }

    @Test
    void detectsNodeTypeChangeAtSamePosition() {
        PlanNode baseline = withChildren("Sort", leaf("Seq Scan", "orders"));
        PlanNode candidate = withChildren("Sort", leaf("Index Scan", "orders"));

        List<PlanDiff> diffs = comparator.compare(baseline, candidate);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).changeType()).isEqualTo(PlanChangeType.NODE_TYPE_CHANGED);
        assertThat(diffs.get(0).baselineNodeType()).isEqualTo("Seq Scan");
        assertThat(diffs.get(0).candidateNodeType()).isEqualTo("Index Scan");
    }

    @Test
    void detectsAddedNodeWhenCandidateHasExtraChild() {
        PlanNode baseline = withChildren("Bitmap Heap Scan");
        PlanNode candidate = withChildren("Bitmap Heap Scan", leaf("Bitmap Index Scan", null));

        List<PlanDiff> diffs = comparator.compare(baseline, candidate);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.get(0).changeType()).isEqualTo(PlanChangeType.NODE_ADDED);
        assertThat(diffs.get(0).candidateNodeType()).isEqualTo("Bitmap Index Scan");
    }

    @Test
    void detectsRemovedNodeWhenBaselineHasExtraChild() {
        PlanNode baseline = withChildren("Sort", leaf("Seq Scan", "orders"));
        PlanNode candidate = leaf("Index Scan", "orders");

        List<PlanDiff> diffs = comparator.compare(baseline, candidate);

        // root itself changed type, and baseline's child has no counterpart -> removed
        assertThat(diffs).anySatisfy(d -> assertThat(d.changeType()).isEqualTo(PlanChangeType.NODE_TYPE_CHANGED));
        assertThat(diffs).anySatisfy(d -> assertThat(d.changeType()).isEqualTo(PlanChangeType.NODE_REMOVED));
    }

    @Test
    void summaryDescribesRealSeqScanToBitmapChange() {
        PlanNode baseline = withChildren("Sort", leaf("Seq Scan", "orders"));
        PlanNode candidate = withChildren("Sort",
                withChildren("Bitmap Heap Scan", leaf("Bitmap Index Scan", null)));

        List<PlanDiff> diffs = comparator.compare(baseline, candidate);
        String summary = comparator.summarize(diffs);

        assertThat(summary).contains("Seq Scan -> Bitmap Heap Scan");
        assertThat(summary).contains("Bitmap Index Scan step added");
    }
}
