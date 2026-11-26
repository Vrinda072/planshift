"""Scores ways of choosing which two candidate indexes to benchmark, against measurements of all of them.

    python3 bench/analyze_study.py bench/results/study.csv

The definitions below were written before the study's results existed.
"""

import csv
import itertools
import json
import math
import os
import statistics
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from index_study import QUERIES, candidates  # noqa: E402

TRIALS = 2
USEFUL = 0.5       # a query is worth indexing if its best candidate saves at least half the time
NEAR_BEST = 0.9    # a pick "finds the best" if it gets at least 90% of the best saving
SLOWDOWN = -0.3    # a pick is harmful if it makes the query at least 30% slower
RIDGE = 1.0


def saved(log_speedup):
    """Fraction of the query's time saved; negative means slower."""
    return 1 - math.exp(-log_speedup)


def load(path):
    runs = defaultdict(dict)
    rows = list(csv.DictReader(open(path)))
    for row in rows:
        runs[(row["query"], row["index"])][row["pass"]] = math.log(float(row["before_ms"]) / float(row["after_ms"]))
    known = json.load(open(os.path.splitext(path)[0] + "-estimates.json"))
    return rows, runs, known


# ---------- ways of choosing ----------

def random_scores(query, options, known):
    return [0.0 for _ in options]


def estimator_scores(query, options, known):
    """PlanShift's own estimate with no history: index shape first, then a rule of thumb from selectivity."""
    return [-known[query[0] + "|" + ",".join(c)]["predicted"] for c in options]


def shape(query, columns):
    """How an index could serve a query, worked out the same way as QueryFeatures.java."""
    _, _, filter_column, operator, _, order, _, aggregate, _ = query
    kind = "EQUALITY" if operator == "=" else "RANGE" if operator in (">", "<", ">=", "<=") else "OTHER"
    if columns[0] == filter_column and kind != "OTHER":
        access = "FILTER"
    else:
        access = "ORDER" if not aggregate and columns[0] == order else "NONE"
    covers = kind == "EQUALITY" and access == "FILTER" and len(columns) > 1 and columns[1] == order
    return access, kind, covers


def estimate_with_history(query, columns, known, history):
    """The decision ImpactPredictionService makes, given past runs as (query, columns, percentage change)."""
    selectivity = known[query[0] + "|" + ",".join(columns)]["selectivity"]
    mine = shape(query, columns)
    if mine[0] == "NONE":
        return 0.0

    def log_selectivity(value):
        return math.log10(max(value, 1e-6))

    neighbours = [change for other, other_columns, change in history
                  if shape(other, other_columns) == mine
                  and abs(log_selectivity(known[other[0] + "|" + ",".join(other_columns)]["selectivity"])
                          - log_selectivity(selectivity)) <= 0.5]
    if len(neighbours) >= 3:
        return statistics.median(neighbours)
    return -25.0 if mine[0] == "ORDER" else -min(70.0, max(3.0, (1 - selectivity) * 45))


def history_of(rows):
    """Every study run as the estimator would have it on record."""
    by_id = {q[0]: q for q in QUERIES}
    return [(by_id[r["query"]], r["index"].split(","), float(r["change_percent"])) for r in rows]


def history_scores_for(history, known):
    """The estimator with every other query's runs on record, and none of the query being ranked."""
    def scores(query, options, _known):
        past = [h for h in history if h[0][0] != query[0]]
        return [-estimate_with_history(query, c, known, past) for c in options]

    return scores


def family(query):
    """Queries of the same shape: kind of filter, whether rows come back ordered, whether they are only counted."""
    _, _, _, operator, _, order, _, aggregate, _ = query
    return ("equality" if operator == "=" else "range", bool(order) and not aggregate, bool(aggregate))


def filter_rule_scores(query, options, known):
    """First the filter column alone, then the filter column followed by the other column that matters."""
    def score(columns):
        if columns == [query[2]]:
            return 2.0
        return 1.0 if len(columns) == 2 and columns[0] == query[2] else 0.0
    return [score(c) for c in options]


def features(query, columns, known):
    _, _, filter_column, operator, _, order, _, aggregate, limit = query
    info = known[query[0] + "|" + ",".join(columns)]
    rarity = -math.log10(max(info["selectivity"], 1e-6)) / 6
    leads_filter = columns[0] == filter_column
    leads_order = not aggregate and columns[0] == order
    equality = operator == "="
    covers = equality and leads_filter and len(columns) > 1 and columns[1] == order
    return [
        1.0,
        float(leads_filter and equality),
        float(leads_filter and not equality),
        float(leads_filter) * rarity,
        float(leads_order),
        float(leads_order) * rarity,
        float(covers),
        float(covers) * (1 - rarity),
        float(len(columns) > 1 and columns[1] == filter_column),
        float(len(columns) > 1),
        float(bool(aggregate)),
        math.log10(max(info["table_rows"], 1)) / 6,
        math.log10(limit) / 4,
    ]


def solve(matrix, vector):
    n = len(vector)
    a = [row[:] + [v] for row, v in zip(matrix, vector)]
    for col in range(n):
        pivot = max(range(col, n), key=lambda r: abs(a[r][col]))
        a[col], a[pivot] = a[pivot], a[col]
        for r in range(n):
            if r != col:
                factor = a[r][col] / a[col][col]
                a[r] = [x - factor * y for x, y in zip(a[r], a[col])]
    return [a[i][n] / a[i][i] for i in range(n)]


def fit(examples):
    """Ridge regression of the measured log speedup on index and query features."""
    width = len(examples[0][0])
    gram = [[sum(x[i] * x[j] for x, _ in examples) + (RIDGE if i == j and i > 0 else 1e-9 if i == j else 0)
             for j in range(width)] for i in range(width)]
    return solve(gram, [sum(x[i] * y for x, y in examples) for i in range(width)])


def ranker_scores_for(group_of, truth, known):
    """A ranker that, for each query, is trained only on queries outside that query's group."""
    cache = {}

    def scores(query, options, _known):
        held_out = group_of(query)
        if held_out not in cache:
            examples = [(features(q, c, known), max(-1.0, min(6.0, truth[(q[0], ",".join(c))])))
                        for q in QUERIES if group_of(q) != held_out
                        for c in candidates(q[1], q[2], q[5])]
            cache[held_out] = fit(examples)
        weights = cache[held_out]
        return [sum(w * x for w, x in zip(weights, features(query, c, known))) for c in options]

    return scores


# ---------- scoring ----------

def possible_picks(scores):
    """Every equally likely ordered choice of TRIALS candidates when ties are broken at random."""
    best = None
    picks = []
    for choice in itertools.permutations(range(len(scores)), TRIALS):
        key = tuple(round(scores[i], 9) for i in choice)
        if best is None or key > best:
            best, picks = key, [choice]
        elif key == best:
            picks.append(choice)
    return picks


def evaluate(scorer, truth, known, queries, useful_at):
    recovery, found, harmful_first, harmful_any = [], [], [], []
    for query in queries:
        options = candidates(query[1], query[2], query[5])
        gains = [saved(truth[(query[0], ",".join(c))]) for c in options]
        picks = possible_picks(scorer(query, options, known))
        harmful_first.append(statistics.mean(gains[p[0]] <= SLOWDOWN for p in picks))
        harmful_any.append(statistics.mean(any(gains[i] <= SLOWDOWN for i in p) for p in picks))
        best = max(max(gains), 0)
        if best < useful_at:
            continue
        reached = [max(0, max(gains[i] for i in p)) / best for p in picks]
        recovery.append(statistics.mean(reached))
        found.append(statistics.mean(r >= NEAR_BEST for r in reached))
    return {"recovery": statistics.mean(recovery), "found": statistics.mean(found),
            "harmful_first": statistics.mean(harmful_first), "harmful_any": statistics.mean(harmful_any),
            "useful": len(recovery)}


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "bench/results/study.csv"
    rows, runs, known = load(path)
    passes = sorted({r["pass"] for r in rows})
    truth = {key: statistics.mean(by_pass.values()) for key, by_pass in runs.items()}

    methods = [
        ("Two at random", random_scores),
        ("PlanShift estimator, no history", estimator_scores),
        ("PlanShift estimator, history from the other queries", history_scores_for(history_of(rows), known)),
        ("Filter-column rule", filter_rule_scores),
        ("Learned ranker, held-out table", ranker_scores_for(lambda q: q[1], truth, known)),
        ("Learned ranker, held-out dataset",
         ranker_scores_for(lambda q: "uber" if q[1] == "uber_pickups" else "shop", truth, known)),
        ("Learned ranker, held-out query family", ranker_scores_for(family, truth, known)),
    ]

    # The planner-based estimate PlanShift gained after the study, collected by planner_estimates.py.
    planner_path = os.path.splitext(path)[0] + "-planner-estimates.json"
    planner = json.load(open(planner_path)) if os.path.exists(planner_path) else None
    if planner:
        methods.append(("PlanShift estimator, asking the planner (HypoPG)",
                        lambda q, options, _known: [-round(planner[q[0] + "|" + ",".join(c)], 2) for c in options]))

    print(f"{len(QUERIES)} queries, {len(truth)} query/index pairs, {len(passes)} passes, {len(rows)} experiments\n")
    for threshold in (USEFUL, 0.3):
        count = evaluate(random_scores, truth, known, QUERIES, threshold)["useful"]
        print(f"Queries where the best candidate saves at least {threshold:.0%} of the time: {count} of {len(QUERIES)}")
        print("| Method (2 trials) | Share of best saving recovered | Found a near-best index "
              "| First pick 30%+ slower | Either pick 30%+ slower |")
        print("|---|---:|---:|---:|---:|")
        for name, scorer in methods:
            m = evaluate(scorer, truth, known, QUERIES, threshold)
            print(f"| {name} | {m['recovery']:.0%} | {m['found']:.0%} | {m['harmful_first']:.1%} "
                  f"| {m['harmful_any']:.1%} |")
        print()

    if len(passes) >= 2:
        a, b = passes[0], passes[1]
        agree = total = 0
        for query in QUERIES:
            keys = [(query[0], ",".join(c)) for c in candidates(query[1], query[2], query[5])]
            second = {k: saved(runs[k][b]) for k in keys}
            if max(second.values()) < USEFUL:
                continue
            chosen = max(keys, key=lambda k: runs[k][a])
            total += 1
            agree += second[chosen] >= NEAR_BEST * max(second.values())
        gaps = sorted(abs(saved(runs[k][a]) - saved(runs[k][b])) for k in runs)
        print("Repeatability between the first two passes:")
        print(f"- the index that won pass {a} was still near-best in pass {b} for {agree} of {total} useful queries")
        print(f"- difference in saving for the same pair: median {gaps[len(gaps) // 2]:.1%} points, "
              f"90th percentile {gaps[int(len(gaps) * 0.9)]:.1%}")

    # How close the estimator's numbers are, not just how it ranks: error in percentage points of time saved.
    history = history_of(rows)
    errors = {"no history": [], "history from the other queries": []}
    for query in QUERIES:
        past = [h for h in history if h[0][0] != query[0]]
        for columns in candidates(query[1], query[2], query[5]):
            actual = -100 * saved(truth[(query[0], ",".join(columns))])
            errors["no history"].append(
                abs(known[query[0] + "|" + ",".join(columns)]["predicted"] - actual))
            errors["history from the other queries"].append(
                abs(estimate_with_history(query, columns, known, past) - actual))
    print("\nEstimator's error against the measured change, median over all pairs (percentage points):")
    if planner:
        errors["asking the planner (its cost change, read as a time change)"] = [
            abs(planner[q[0] + "|" + ",".join(c)] + 100 * saved(truth[(q[0], ",".join(c))]))
            for q in QUERIES for c in candidates(q[1], q[2], q[5])]
    for name, values in errors.items():
        print(f"- {name}: {statistics.median(values):.0f}")
    print(f"Query families held out in turn: {len({family(q) for q in QUERIES})}")

    unchanged = [r for r in rows if r["plan_changed"] == "False"]
    print(f"\nRuns where the plan did not change: {len(unchanged)}; of those, "
          f"{sum(abs(float(r['change_percent'])) > 20 for r in unchanged)} moved more than 20% and "
          f"{sum(r['status'] != 'UNCHANGED' for r in unchanged)} were still reported as a change.")

    seconds = [v["seconds"] for v in known.values()]
    walls = [float(r["wall_seconds"]) for r in rows]
    per_query = statistics.mean(len(candidates(q[1], q[2], q[5])) for q in QUERIES)
    print(f"Estimate time per candidate: median {statistics.median(seconds) * 1000:.0f} ms. "
          f"Measuring one candidate: median {statistics.median(walls):.1f} s, "
          f"so about {per_query * statistics.median(walls):.0f} s to try all {per_query:.1f} per query.")

    print("\nPer query, best measured candidate:")
    print("| Query | Table | Filter | Order | Best index | Time saved | Index size | Build |")
    print("|---|---|---|---|---|---:|---:|---:|")
    for query in QUERIES:
        keys = [",".join(c) for c in candidates(query[1], query[2], query[5])]
        winner = max(keys, key=lambda k: truth[(query[0], k)])
        mine = [r for r in rows if r["query"] == query[0] and r["index"] == winner]
        gain = saved(truth[(query[0], winner)])
        label = f"`({winner.replace(',', ', ')})`" if gain >= USEFUL else "none clearly useful"
        print(f"| {query[0]} | {query[1]} | {query[2]} {query[3]} | {query[5] or ''} | {label} | {gain:.0%} "
              f"| {int(mine[0]['index_bytes']) / 1048576:.1f} MB "
              f"| {statistics.median(float(r['build_ms']) for r in mine):.0f} ms |")


if __name__ == "__main__":
    main()
