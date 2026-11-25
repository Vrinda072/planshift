"""Does the order of the timed runs change how often noise gets reported?

Measures comparisons where nothing relevant changes, in both run orders, shuffled together:

- control: the built-in workload as an A/A run, with no change at all;
- unused:  a query with an index it has no use for, so the plan is the same on both sides. In the taking-turns order
  this also checks that hiding the index doesn't by itself make one side faster.

Needs the same data as index_study.py.

    python3 bench/mode_check.py --rounds 6 --reps 10 --out bench/results/run-order.csv
    python3 bench/mode_check.py --summarise bench/results/run-order.csv
"""

import argparse
import csv
import random
import statistics

from index_study import QUERIES, spec_of
from planshift import PlanShift

# query id -> a column it neither filters nor sorts on
UNUSED = {"O1": "status", "O4": "status", "O8": "total_amount", "I1": "quantity", "I3": "quantity",
          "C1": "last_name", "C3": "city", "U1": "base", "U6": "lon", "U2": "base"}
MODES = ["BLOCKS", "ALTERNATING"]
FIELDS = ["kind", "mode", "experiment_id", "query", "before_ms", "after_ms", "change_percent", "evidence", "status",
          "plan_changed"]


def summarise(rows, threshold=20):
    for kind in ("control", "unused"):
        print(f"\n{kind}:")
        print("| Run order | Comparisons | Moved more than 20% | Still reported | Typical change | 9 in 10 within "
              "| Average direction |")
        print("|---|---:|---:|---:|---:|---:|---:|")
        for mode in MODES:
            mine = [r for r in rows if r["kind"] == kind and r["mode"] == mode and str(r["plan_changed"]) == "False"]
            if not mine:
                continue
            signed = [float(r["change_percent"]) for r in mine]
            size = sorted(abs(c) for c in signed)
            past = sum(c > threshold for c in size)
            reported = sum(r["status"] != "UNCHANGED" for r in mine)
            print(f"| {mode} | {len(mine)} | {past} ({past / len(mine):.0%}) | {reported} ({reported / len(mine):.0%}) "
                  f"| {statistics.median(size):.1f}% | {size[int(len(size) * 0.9)]:.1f}% "
                  f"| {statistics.median(signed):+.1f}% |")
        dropped = sum(r["kind"] == kind and str(r["plan_changed"]) != "False" for r in rows)
        if dropped:
            print(f"({dropped} left out because the plan changed)")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--rounds", type=int, default=6)
    parser.add_argument("--reps", type=int, default=10)
    parser.add_argument("--seed", type=int, default=11)
    parser.add_argument("--out", default="bench/results/run-order.csv")
    parser.add_argument("--summarise", metavar="CSV", help="only print the tables for an existing results file")
    args = parser.parse_args()
    if args.summarise:
        summarise(list(csv.DictReader(open(args.summarise))))
        return

    api = PlanShift(args.api)
    queries = {q[0]: q for q in QUERIES}
    order = random.Random(args.seed)
    rows = []
    with open(args.out, "w", newline="") as f:
        writer = csv.DictWriter(f, FIELDS)
        writer.writeheader()
        for n in range(1, args.rounds + 1):
            trials = [("unused", mode, query_id) for mode in MODES for query_id in UNUSED]
            trials += [("control", mode, None) for mode in MODES]
            order.shuffle(trials)
            for kind, mode, query_id in trials:
                if kind == "control":
                    experiment = api.run("/api/experiments",
                                         {"repetitions": args.reps, "control": True, "measurement": mode})
                else:
                    experiment = api.run("/api/query-builder/run",
                                         {"spec": spec_of(queries[query_id]), "indexColumns": [UNUSED[query_id]],
                                          "repetitions": args.reps, "measurement": mode})
                assert experiment["measurement"] == mode
                for result in experiment["queryResults"]:
                    row = {"kind": kind, "mode": mode, "experiment_id": experiment["experimentId"],
                           "query": query_id or result["queryName"],
                           "before_ms": round(result["baselineMedianMs"], 4),
                           "after_ms": round(result["candidateMedianMs"], 4),
                           "change_percent": round(result["percentageChange"], 2),
                           "evidence": result["timing"]["evidence"], "status": result["status"],
                           "plan_changed": not result["planDiffSummary"].startswith("No change")}
                    writer.writerow(row)
                    rows.append(row)
                f.flush()
            print(f"round {n}/{args.rounds} done", flush=True)
    summarise(rows)


if __name__ == "__main__":
    main()
