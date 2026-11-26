"""Asks a running PlanShift for its planner-based estimate of every pair in the first study, so it can be scored
against what was measured then. Needs the same data, and a Postgres with HypoPG.

    python3 bench/planner_estimates.py
"""

import argparse
import json

from index_study import QUERIES, candidates, spec_of
from planshift import PlanShift


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--out", default="bench/results/study-planner-estimates.json")
    args = parser.parse_args()
    api = PlanShift(args.api)
    found = {}
    for query in QUERIES:
        for columns in candidates(query[1], query[2], query[5]):
            estimate = api.call("/api/query-builder/predict-impact",
                                {"spec": spec_of(query), "indexColumns": columns})
            if estimate["method"] != "planner":
                raise SystemExit(f"expected a planner estimate, got {estimate}")
            found[query[0] + "|" + ",".join(columns)] = estimate["predictedPercentageChange"]
    with open(args.out, "w") as f:
        json.dump(found, f, indent=1)
    print(f"saved {len(found)} estimates to {args.out}")


if __name__ == "__main__":
    main()
