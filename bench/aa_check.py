"""How often does PlanShift report a change when nothing changed?

Runs the built-in workload as A/A controls and counts the per-query "changes". Needs the built-in dataset loaded.

    python3 bench/aa_check.py --runs 10 --reps 10 --out bench/results/aa.csv
"""

import argparse
import csv

from planshift import PlanShift

FIELDS = ["experiment_id", "query", "before_ms", "after_ms", "change_percent", "p_value", "evidence", "status"]


def rows_of(experiment):
    for r in experiment["queryResults"]:
        yield {
            "experiment_id": experiment["experimentId"],
            "query": r["queryName"],
            "before_ms": round(r["baselineMedianMs"], 4),
            "after_ms": round(r["candidateMedianMs"], 4),
            "change_percent": round(r["percentageChange"], 2),
            "p_value": round(r["timing"]["pValue"], 5),
            "evidence": r["timing"]["evidence"],
            "status": r["status"],
        }


def summarise(rows, threshold_percent=20):
    total = len(rows)
    past = sum(abs(float(r["change_percent"])) > threshold_percent for r in rows)
    reported = sum(r["status"] != "UNCHANGED" for r in rows)
    separated = sum(r["evidence"] == "CLEAR" for r in rows)
    changes = sorted(abs(float(r["change_percent"])) for r in rows)
    print(f"comparisons with nothing changed:       {total}")
    print(f"past the {threshold_percent}% threshold:                 {past}")
    print(f"still reported after the rank-sum test: {reported}")
    print(f"called separated by the test (p<0.05):  {separated}")
    print(f"|change|: median {changes[total // 2]:.1f}%, 90th percentile {changes[int(total * 0.9)]:.1f}%, "
          f"max {changes[-1]:.1f}%")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--runs", type=int, default=10)
    parser.add_argument("--reps", type=int, default=10)
    parser.add_argument("--out", default="bench/results/aa.csv")
    args = parser.parse_args()

    api = PlanShift(args.api)
    rows = []
    for n in range(args.runs):
        experiment = api.run("/api/experiments", {"repetitions": args.reps, "control": True})
        rows.extend(rows_of(experiment))
        print(f"run {n + 1}/{args.runs}: median drift {experiment['overallPercentageChange']:+.1f}%", flush=True)

    with open(args.out, "w", newline="") as f:
        writer = csv.DictWriter(f, FIELDS)
        writer.writeheader()
        writer.writerows(rows)
    summarise(rows)


if __name__ == "__main__":
    main()
