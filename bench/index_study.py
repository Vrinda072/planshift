"""Measures every candidate index for a fixed set of queries, using PlanShift itself.

Needs the built-in dataset at full scale and the Uber pickups CSV imported as `uber_pickups` (see docs/index-study.md).
The set of queries and the rule for generating candidates are fixed here, before any result is seen.

    python3 bench/index_study.py --passes 2 --reps 9 --out bench/results/study.csv
"""

import argparse
import csv
import json
import os
import random
import time

from planshift import PlanShift

# Per table, a column used as the "unrelated" candidate when a query leaves one free.
OTHER_COLUMNS = {
    "orders": ["status", "total_amount"],
    "order_items": ["quantity", "unit_price"],
    "customers": ["city", "last_name"],
    "uber_pickups": ["base", "lon"],
}

# (id, table, filter column, operator, value, order column, direction, aggregate, limit)
QUERIES = [
    ("O1", "orders", "customer_id", "=", "500", None, None, None, 100),
    ("O2", "orders", "customer_id", "=", "500", "order_date", "DESC", None, 20),
    ("O3", "orders", "customer_id", "=", "4242", None, None, "COUNT", 1),
    ("O4", "orders", "total_amount", ">", "7600", None, None, None, 1000),
    ("O5", "orders", "total_amount", ">", "6000", "order_date", "DESC", None, 50),
    ("O6", "orders", "order_date", ">", "2025-11-24", None, None, None, 5000),
    ("O7", "orders", "status", "=", "PENDING", "total_amount", "DESC", None, 10),
    ("O8", "orders", "status", "=", "SHIPPED", None, None, "COUNT", 1),
    ("I1", "order_items", "unit_price", ">", "499", None, None, None, 5000),
    ("I2", "order_items", "quantity", "=", "4", "unit_price", "DESC", None, 10),
    ("I3", "order_items", "unit_price", "<", "7", None, None, "COUNT", 1),
    ("I4", "order_items", "quantity", "=", "2", None, None, None, 100),
    ("C1", "customers", "city", "=", "Houston", None, None, None, 5000),
    ("C2", "customers", "state", "=", "England", "signup_date", "DESC", None, 10),
    ("C3", "customers", "last_name", "=", "Brown", None, None, "COUNT", 1),
    ("C4", "customers", "signup_date", ">", "2023-12-29", None, None, None, 5000),
    ("C5", "customers", "country", "=", "Canada", "last_name", "ASC", None, 20),
    ("C6", "customers", "city", "=", "Bengaluru", "signup_date", "ASC", None, 50),
    ("U1", "uber_pickups", "lat", "=", "40.7267", None, None, None, 1000),
    ("U2", "uber_pickups", "lon", "=", "-73.9888", None, None, None, 1000),
    ("U3", "uber_pickups", "lat", ">", "41.0168", None, None, None, 1000),
    ("U4", "uber_pickups", "lon", "<", "-74.2551", "lat", "ASC", None, 100),
    ("U5", "uber_pickups", "base", "=", "B02764", "lat", "DESC", None, 10),
    ("U6", "uber_pickups", "base", "=", "B02512", None, None, "COUNT", 1),
    ("U7", "uber_pickups", "date_time", "=", "4/7/2014 20:21:00", None, None, None, 1000),
    ("U8", "uber_pickups", "lat", "=", "40.7500", "lon", "ASC", None, 20),
]


def candidates(table, filter_column, order_column):
    """Filter column, an unrelated column, and both column orders of the two columns that matter."""
    free = [c for c in OTHER_COLUMNS[table] if c not in (filter_column, order_column)]
    partner = order_column if order_column and order_column != filter_column else free[0]
    result = [[filter_column]]
    if free:  # a query can use both of a table's spare columns, leaving none unrelated
        result.append([free[0]])
    if [partner] not in result:
        result.append([partner])
    return result + [[filter_column, partner], [partner, filter_column]]


def spec_of(query):
    _, table, column, operator, value, order, direction, aggregate, limit = query
    return {"table": table, "filterColumn": column, "filterOperator": operator, "filterValue": value,
            "orderByColumn": order, "orderByDirection": direction, "aggregateFunction": aggregate, "limit": limit}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--api", default="http://localhost:8080")
    parser.add_argument("--passes", type=int, default=2)
    parser.add_argument("--reps", type=int, default=9)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--out", default="bench/results/study.csv")
    args = parser.parse_args()
    api = PlanShift(args.api)

    row_counts = {t["tableName"]: t["rowEstimate"] for t in api.call("/api/schema/tables")}
    trials = [(q, c) for q in QUERIES for c in candidates(q[1], q[2], q[5])]

    # What a recommender is allowed to know before running anything. Gathered first so that no study run is
    # already in the history the built-in estimator reads.
    known_path = os.path.splitext(args.out)[0] + "-estimates.json"
    known = {}
    for query, columns in trials:
        started = time.perf_counter()
        estimate = api.call("/api/query-builder/predict-impact", {"spec": spec_of(query), "indexColumns": columns})
        known[query[0] + "|" + ",".join(columns)] = {
            "predicted": estimate["predictedPercentageChange"], "method": estimate["method"],
            "selectivity": estimate["selectivity"], "seconds": time.perf_counter() - started,
            "table_rows": row_counts[query[1]]}
    with open(known_path, "w") as f:
        json.dump(known, f, indent=1)

    fields = ["pass", "query", "table", "index", "experiment_id", "before_ms", "after_ms", "change_percent",
              "evidence", "status", "plan_changed", "index_bytes", "build_ms", "wall_seconds"]
    with open(args.out, "w", newline="") as f:
        writer = csv.DictWriter(f, fields)
        writer.writeheader()
        order = random.Random(args.seed)
        for n in range(1, args.passes + 1):
            shuffled = trials[:]
            order.shuffle(shuffled)  # so that no candidate is always measured at the same point in a pass
            for i, (query, columns) in enumerate(shuffled, 1):
                started = time.time()
                experiment = api.run("/api/query-builder/run",
                                     {"spec": spec_of(query), "indexColumns": columns, "repetitions": args.reps})
                result = experiment["queryResults"][0]
                writer.writerow({
                    "pass": n, "query": query[0], "table": query[1], "index": ",".join(columns),
                    "experiment_id": experiment["experimentId"],
                    "before_ms": round(result["baselineMedianMs"], 4),
                    "after_ms": round(result["candidateMedianMs"], 4),
                    "change_percent": round(result["percentageChange"], 2),
                    "evidence": result["timing"]["evidence"], "status": result["status"],
                    "plan_changed": not result["planDiffSummary"].startswith("No change"),
                    "index_bytes": experiment["indexSizeBytes"], "build_ms": round(experiment["indexBuildMs"], 1),
                    "wall_seconds": round(time.time() - started, 1)})
                f.flush()
                if i % 20 == 0 or i == len(shuffled):
                    print(f"pass {n}: {i}/{len(shuffled)}", flush=True)


if __name__ == "__main__":
    main()
