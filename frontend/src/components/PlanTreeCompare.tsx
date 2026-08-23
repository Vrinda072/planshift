import type { PlanNode } from "../api/types";

function PlanNodeBox({ node, changed }: { node: PlanNode | null; changed: boolean }) {
  if (!node) {
    return <div className="plan-node" style={{ opacity: 0.35 }}>—</div>;
  }
  return (
    <div className={`plan-node${changed ? " changed" : ""}`}>
      <div className="plan-node-type">{node.nodeType}</div>
      {node.relationName && <div className="plan-node-detail">on {node.relationName}</div>}
      {node.indexName && <div className="plan-node-detail">via {node.indexName}</div>}
      {node.filter && <div className="plan-node-detail">filter: {node.filter}</div>}
      {node.indexCondition && <div className="plan-node-detail">cond: {node.indexCondition}</div>}
      <div className="plan-node-detail">
        {node.actualRows} rows &middot; {node.actualTotalTimeMs.toFixed(3)} ms
      </div>
    </div>
  );
}

function NodePair({ base, cand }: { base: PlanNode | null; cand: PlanNode | null }) {
  const changed = (base?.nodeType ?? null) !== (cand?.nodeType ?? null);
  const childCount = Math.max(base?.children.length ?? 0, cand?.children.length ?? 0);

  return (
    <div>
      <div className="plan-columns">
        <PlanNodeBox node={base} changed={changed} />
        <PlanNodeBox node={cand} changed={changed} />
      </div>
      {childCount > 0 && (
        <div className="plan-child-group">
          {Array.from({ length: childCount }, (_, i) => (
            <div key={i}>
              <div className="plan-arrow">&darr;</div>
              <NodePair base={base?.children[i] ?? null} cand={cand?.children[i] ?? null} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function PlanTreeCompare({
  baselinePlanJson,
  candidatePlanJson,
}: {
  baselinePlanJson: string;
  candidatePlanJson: string;
}) {
  let base: PlanNode | null = null;
  let cand: PlanNode | null = null;
  try {
    base = JSON.parse(baselinePlanJson);
    cand = JSON.parse(candidatePlanJson);
  } catch {
    return <div className="empty-state">Plan data unavailable.</div>;
  }

  return (
    <div>
      <div className="plan-columns">
        <div className="plan-col-label">Before</div>
        <div className="plan-col-label">After</div>
      </div>
      <NodePair base={base} cand={cand} />
    </div>
  );
}
