package com.planshift.queryplan;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares two plan trees for the same query (baseline vs. candidate
 * configuration) and reports what changed.
 *
 * This uses a simple recursive positional walk rather than a full tree-edit-
 * distance algorithm (e.g. Zhang-Shasha, O(n^3)): at each level we compare
 * children pairwise by position, treating an extra child on either side as
 * added/removed. Postgres plan trees here are shallow (a handful of nodes,
 * rarely more than 2-3 children at any level), so the positional walk is
 * O(n) in the number of nodes and easy to reason about. It would give
 * misleading results on trees where subtrees get reordered or a node is
 * inserted in the middle of a sibling list -- not a concern for comparing
 * the same query's plan under two configurations, but worth naming as the
 * simplification it is.
 */
@Component
public class PlanComparator {

    public List<PlanDiff> compare(PlanNode baseline, PlanNode candidate) {
        List<PlanDiff> diffs = new ArrayList<>();
        walk(baseline, candidate, "root", diffs);
        return diffs;
    }

    private void walk(PlanNode baseline, PlanNode candidate, String path, List<PlanDiff> diffs) {
        if (baseline == null && candidate == null) {
            return;
        }
        if (baseline == null) {
            diffs.add(new PlanDiff(PlanChangeType.NODE_ADDED, path, null, candidate.nodeType(),
                    candidate.nodeType() + " added"));
            return;
        }
        if (candidate == null) {
            diffs.add(new PlanDiff(PlanChangeType.NODE_REMOVED, path, baseline.nodeType(), null,
                    baseline.nodeType() + " removed"));
            return;
        }

        if (!baseline.nodeType().equals(candidate.nodeType())) {
            diffs.add(new PlanDiff(PlanChangeType.NODE_TYPE_CHANGED, path, baseline.nodeType(), candidate.nodeType(),
                    baseline.nodeType() + " -> " + candidate.nodeType()));
        }

        int maxChildren = Math.max(baseline.children().size(), candidate.children().size());
        for (int i = 0; i < maxChildren; i++) {
            PlanNode baseChild = i < baseline.children().size() ? baseline.children().get(i) : null;
            PlanNode candChild = i < candidate.children().size() ? candidate.children().get(i) : null;
            walk(baseChild, candChild, path + " > child[" + i + "]", diffs);
        }
    }

    /**
     * A short, factual summary derived only from the actual diffs -- no
     * invented explanation beyond what the plan comparison found.
     */
    public String summarize(List<PlanDiff> diffs) {
        if (diffs.isEmpty()) {
            return "No change in query plan structure.";
        }
        List<String> parts = new ArrayList<>();
        for (PlanDiff diff : diffs) {
            parts.add(switch (diff.changeType()) {
                case NODE_TYPE_CHANGED -> "Scan/execution strategy changed: " + diff.baselineNodeType() + " -> " + diff.candidateNodeType();
                case NODE_ADDED -> diff.candidateNodeType() + " step added";
                case NODE_REMOVED -> diff.baselineNodeType() + " step removed";
            });
        }
        return String.join("; ", parts);
    }
}
