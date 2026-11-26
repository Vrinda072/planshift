"""Picking two indexes for joins and multi-column filters, against Postgres's own planner as the baseline.

The queries, the rule that generates candidates and both ways of ranking them are fixed in this file, before any
result is seen. It runs against a throwaway Postgres with the HypoPG extension, holding a copy of the data, and
needs Docker plus the data index_study.py needs, loaded in the local `planshift-postgres` container.

    python3 bench/hard_study.py setup
    python3 bench/hard_study.py measure --passes 2 --reps 9 --out bench/results/hard-study.csv
    python3 bench/hard_study.py analyze bench/results/hard-study.csv
"""

import argparse
import csv
import itertools
import json
import math
import os
import random
import statistics
import subprocess
import time
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = "planshift-postgres"
CONTAINER = "planshift-hypopg"
IMAGE = "planshift-hypopg"
TABLES = ["customers", "products", "orders", "order_items", "uber_pickups"]
# What the schema ships with besides primary keys. Anything else in the copy is removed.
SHIPPED_INDEXES = {"idx_order_items_order_id": ("order_items", ("order_id",)),
                   "idx_order_items_product_id": ("order_items", ("product_id",))}
PRIMARY_KEYS = {"customers": "customer_id", "products": "product_id", "orders": "order_id",
                "order_items": "order_item_id", "uber_pickups": None}

# How a query uses a column. The strongest use wins if there are several.
EQ, JOIN, RANGE, ORDER = "eq", "join", "range", "order"
STRENGTH = [EQ, JOIN, RANGE, ORDER]

CO = "FROM customers c JOIN orders o ON o.customer_id = c.customer_id"
OI = "FROM orders o JOIN order_items i ON i.order_id = o.order_id"


def uses(**tables):
    return {table: dict(columns) for table, columns in tables.items()}


# (id, SQL, table -> column -> use)
QUERIES = [
    ("H1", "SELECT * FROM orders WHERE customer_id = 500 AND status = 'PENDING'",
     uses(orders={"customer_id": EQ, "status": EQ})),
    ("H2", "SELECT * FROM orders WHERE status = 'PENDING' AND order_date >= '2025-11-01'",
     uses(orders={"status": EQ, "order_date": RANGE})),
    ("H3", "SELECT * FROM orders WHERE status = 'CANCELLED' AND total_amount > 6000",
     uses(orders={"status": EQ, "total_amount": RANGE})),
    ("H4", "SELECT * FROM orders WHERE order_date >= '2025-06-01' AND order_date < '2025-06-03' "
           "AND total_amount > 5000",
     uses(orders={"order_date": RANGE, "total_amount": RANGE})),
    ("H5", "SELECT * FROM orders WHERE status = 'SHIPPED' AND order_date >= '2025-11-01' "
           "ORDER BY total_amount DESC LIMIT 20",
     uses(orders={"status": EQ, "order_date": RANGE, "total_amount": ORDER})),
    ("H6", "SELECT * FROM orders WHERE status = 'COMPLETED' AND total_amount > 7000 "
           "ORDER BY order_date DESC LIMIT 50",
     uses(orders={"status": EQ, "total_amount": RANGE, "order_date": ORDER})),
    ("H7", "SELECT * FROM order_items WHERE product_id = 42 AND quantity = 3",
     uses(order_items={"product_id": EQ, "quantity": EQ})),
    ("H8", "SELECT * FROM order_items WHERE quantity = 5 AND unit_price > 480",
     uses(order_items={"quantity": EQ, "unit_price": RANGE})),
    ("H9", "SELECT COUNT(*) FROM order_items WHERE quantity = 1 AND unit_price < 8",
     uses(order_items={"quantity": EQ, "unit_price": RANGE})),
    ("H10", "SELECT * FROM customers WHERE city = 'Mumbai' AND signup_date >= '2023-10-01'",
     uses(customers={"city": EQ, "signup_date": RANGE})),
    ("H11", "SELECT * FROM customers WHERE country = 'India' AND last_name = 'Sharma'",
     uses(customers={"country": EQ, "last_name": EQ})),
    ("H12", "SELECT * FROM customers WHERE first_name = 'Priya' AND city = 'Delhi' "
            "ORDER BY signup_date DESC LIMIT 10",
     uses(customers={"first_name": EQ, "city": EQ, "signup_date": ORDER})),
    ("H13", "SELECT * FROM uber_pickups WHERE base = 'B02764' AND lat > 40.9",
     uses(uber_pickups={"base": EQ, "lat": RANGE})),
    ("H14", "SELECT * FROM uber_pickups WHERE lat BETWEEN 40.720 AND 40.721 AND lon BETWEEN -73.990 AND -73.989",
     uses(uber_pickups={"lat": RANGE, "lon": RANGE})),
    ("H15", "SELECT COUNT(*) FROM uber_pickups WHERE base = 'B02512' AND lon < -74.2",
     uses(uber_pickups={"base": EQ, "lon": RANGE})),
    ("J1", f"SELECT o.* {CO} WHERE c.last_name = 'Haddad' AND c.city = 'Paris'",
     uses(customers={"customer_id": JOIN, "last_name": EQ, "city": EQ}, orders={"customer_id": JOIN})),
    ("J2", f"SELECT i.* {OI} WHERE o.status = 'PENDING' AND o.total_amount > 7000",
     uses(orders={"order_id": JOIN, "status": EQ, "total_amount": RANGE}, order_items={"order_id": JOIN})),
    ("J3", f"SELECT o.* {CO} WHERE c.country = 'Canada' AND o.order_date >= '2025-11-28'",
     uses(customers={"customer_id": JOIN, "country": EQ}, orders={"customer_id": JOIN, "order_date": RANGE})),
    ("J4", f"SELECT o.* {CO} WHERE c.signup_date >= '2023-12-28' AND o.status = 'SHIPPED'",
     uses(customers={"customer_id": JOIN, "signup_date": RANGE}, orders={"customer_id": JOIN, "status": EQ})),
    ("J5", f"SELECT o.* {CO} WHERE c.city = 'Berlin' ORDER BY o.order_date DESC LIMIT 20",
     uses(customers={"customer_id": JOIN, "city": EQ}, orders={"customer_id": JOIN, "order_date": ORDER})),
    ("J6", f"SELECT o.* {CO} WHERE c.country = 'UK' AND o.total_amount > 7500",
     uses(customers={"customer_id": JOIN, "country": EQ}, orders={"customer_id": JOIN, "total_amount": RANGE})),
    ("J7", "SELECT i.* FROM order_items i JOIN products p ON p.product_id = i.product_id "
           "WHERE p.category = 'Books' AND i.quantity = 5 AND i.unit_price > 450",
     uses(products={"product_id": JOIN, "category": EQ},
          order_items={"product_id": JOIN, "quantity": EQ, "unit_price": RANGE})),
]


def candidates(query):
    """Every index of up to three of the columns the query uses on one table, in every order. The primary key on its
    own and indexes that already exist are left out, as are indexes involving the primary key."""
    result = []
    existing = set(SHIPPED_INDEXES.values())
    for table, columns in query[2].items():
        usable = [c for c in columns if c != PRIMARY_KEYS[table]]
        options = [option for size in (1, 2, 3) for option in itertools.permutations(usable, size)]
        result += [(table, option) for option in options if (table, option) not in existing]
    return result


def label(candidate):
    return candidate[0] + "(" + ",".join(candidate[1]) + ")"


# ---------- the throwaway database ----------

def run(command, **kwargs):
    return subprocess.run(command, check=True, text=True, **kwargs)


def psql(script, container=CONTAINER):
    done = subprocess.run(["docker", "exec", "-i", container, "psql", "-X", "-q", "-At", "-v", "ON_ERROR_STOP=1",
                           "-U", "planshift", "-d", "planshift"], input=script, text=True, capture_output=True)
    if done.returncode != 0:
        raise RuntimeError(done.stderr.strip())
    return done.stdout


def setup(_args):
    run(["docker", "build", "-q", "-t", IMAGE, os.path.join(HERE, "..", "db")])
    subprocess.run(["docker", "rm", "-f", CONTAINER], capture_output=True)
    run(["docker", "run", "-d", "--name", CONTAINER, "-e", "POSTGRES_USER=planshift", "-e", "POSTGRES_DB=planshift",
         "-e", "POSTGRES_HOST_AUTH_METHOD=trust", IMAGE], capture_output=True)
    for _ in range(120):
        # Over TCP, which only the server's final start listens on; its first-run server is socket-only.
        if subprocess.run(["docker", "exec", CONTAINER, "pg_isready", "-h", "127.0.0.1", "-U", "planshift"],
                          capture_output=True).returncode == 0:
            break
        time.sleep(1)
    dump = ["docker", "exec", SOURCE, "pg_dump", "-U", "planshift", "-d", "planshift", "--no-owner"]
    for table in TABLES:
        dump += ["-t", "public." + table, "-t", f"public.{table}_*_seq"]
    source = subprocess.Popen(dump, stdout=subprocess.PIPE)
    run(["docker", "exec", "-i", CONTAINER, "psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-U", "planshift",
         "-d", "planshift"], stdin=source.stdout, capture_output=True)
    if source.wait() != 0:
        raise RuntimeError("pg_dump failed")
    extra = psql("SELECT indexname FROM pg_indexes i WHERE schemaname = 'public' AND NOT EXISTS "
                 "(SELECT 1 FROM pg_constraint k WHERE k.conname = i.indexname)").split()
    for name in extra:
        if name not in SHIPPED_INDEXES:
            psql(f'DROP INDEX "{name}"')
    psql("CREATE EXTENSION hypopg; VACUUM ANALYZE;")
    print(psql("SELECT relname || ' ' || reltuples::bigint FROM pg_class WHERE relkind = 'r' "
               "AND relnamespace = 'public'::regnamespace ORDER BY 1"))


# ---------- what a recommender may know before running anything ----------

def planner_view(query, candidate):
    """Postgres's cost for the query if this index existed, and HypoPG's guess at its size."""
    table, columns = candidate
    statement = f"CREATE INDEX ON {table} ({', '.join(columns)})"
    out = psql(f"SELECT 'size ' || hypopg_relation_size(indexrelid) "
               f"FROM hypopg_create_index($q${statement}$q$);\n"
               f"EXPLAIN (FORMAT JSON) {query[1]};\n")
    first, plan = out.split("\n", 1)
    return {"cost": json.loads(plan)[0]["Plan"]["Total Cost"], "bytes": int(first.split()[1])}


# ---------- measuring ----------

def explain(sql):
    return f"EXPLAIN (ANALYZE, TIMING OFF, FORMAT JSON) {sql};\n"


def measure_one(query, candidate, reps):
    """The same taking-turns order PlanShift offers: hide the index inside a rolled-back transaction."""
    table, columns = candidate
    started = time.time()
    psql(f"CREATE INDEX hs_candidate ON {table} ({', '.join(columns)});")
    build_ms = (time.time() - started) * 1000
    try:
        size = int(psql("SELECT pg_relation_size('hs_candidate')"))
        script = "SET statement_timeout = '120s';\n"
        for round_number in range(reps):
            sides = ["without", "with"] if round_number % 2 == 0 else ["with", "without"]
            for side in sides:
                script += "BEGIN;\n" + ("DROP INDEX hs_candidate;\n" if side == "without" else "")
                script += "\\o /dev/null\n" + explain(query[1]) + "\\o\n"
                script += f"\\echo @@{side}\n" + explain(query[1]) + "ROLLBACK;\n"
        times = defaultdict(list)
        used = False
        for chunk in psql(script).split("@@")[1:]:
            side, text = chunk.split("\n", 1)
            result = json.loads(text)[0]
            times[side].append(result["Execution Time"])
            used = used or (side == "with" and "hs_candidate" in text)
        assert len(times["without"]) == reps and len(times["with"]) == reps
        return {"before_ms": statistics.median(times["without"]), "after_ms": statistics.median(times["with"]),
                "used": used, "index_bytes": size, "build_ms": build_ms}
    finally:
        psql("DROP INDEX IF EXISTS hs_candidate;")


def measure(args):
    known = {}
    for query in QUERIES:
        for candidate in candidates(query):
            started = time.perf_counter()
            known[query[0] + "|" + label(candidate)] = planner_view(query, candidate)
            known[query[0] + "|" + label(candidate)]["seconds"] = time.perf_counter() - started
    with open(os.path.splitext(args.out)[0] + "-planner.json", "w") as f:
        json.dump(known, f, indent=1)

    trials = [(q, c) for q in QUERIES for c in candidates(q)]
    print(f"{len(QUERIES)} queries, {len(trials)} query/index pairs", flush=True)
    fields = ["pass", "query", "index", "before_ms", "after_ms", "used", "index_bytes", "build_ms", "wall_seconds"]
    order = random.Random(args.seed)
    with open(args.out, "w", newline="") as f:
        writer = csv.DictWriter(f, fields)
        writer.writeheader()
        for n in range(1, args.passes + 1):
            shuffled = trials[:]
            order.shuffle(shuffled)
            for i, (query, candidate) in enumerate(shuffled, 1):
                started = time.time()
                result = measure_one(query, candidate, args.reps)
                writer.writerow({"pass": n, "query": query[0], "index": label(candidate),
                                 "before_ms": round(result["before_ms"], 4), "after_ms": round(result["after_ms"], 4),
                                 "used": result["used"], "index_bytes": result["index_bytes"],
                                 "build_ms": round(result["build_ms"], 1),
                                 "wall_seconds": round(time.time() - started, 1)})
                f.flush()
                if i % 25 == 0 or i == len(shuffled):
                    print(f"pass {n}: {i}/{len(shuffled)}", flush=True)


# ---------- ways of choosing ----------

def dense_ranks(keys):
    ordered = sorted(set(keys))
    return [ordered.index(k) for k in keys]


def role_rule_scores(query, options, known):
    """What a practised person would try first: lead with an equality column, then a join, range or ordering column;
    follow it with another equality column, then the ordering, a range, a join. With the same start, the index that
    carries on to a further column goes first."""
    lead = {EQ: 4, JOIN: 3, RANGE: 2, ORDER: 1}
    follow = {EQ: 4, ORDER: 3, RANGE: 2, JOIN: 1}

    def score(candidate):
        table, columns = candidate
        roles = [query[2][table][c] for c in columns]
        rest = [follow[r] for r in roles[1:]] + [0, 0]
        return lead[roles[0]] * 100 + rest[0] * 10 + rest[1]

    return [score(c) for c in options]


def planner_scores(query, options, known):
    """Lowest estimated cost first; among equal costs, the smaller index."""
    views = [known[query[0] + "|" + label(c)] for c in options]
    return dense_ranks([(-round(v["cost"], 2), -v["bytes"]) for v in views])


def random_scores(query, options, known):
    return [0 for _ in options]


def possible_picks(scores, trials):
    """Every equally likely ordered choice of `trials` candidates when ties are broken at random."""
    best, picks = None, []
    for choice in itertools.permutations(range(len(scores)), min(trials, len(scores))):
        key = tuple(scores[i] for i in choice)
        if best is None or key > best:
            best, picks = key, [choice]
        elif key == best:
            picks.append(choice)
    return picks


# ---------- scoring, with the same definitions as analyze_study.py ----------

USEFUL, NEAR_BEST, SLOWDOWN = 0.5, 0.9, -0.3


def evaluate(scorer, truth, known, trials):
    recovery, found, harmful = [], [], []
    for query in QUERIES:
        options = candidates(query)
        gains = [truth[(query[0], label(c))] for c in options]
        picks = possible_picks(scorer(query, options, known), trials)
        harmful.append(statistics.mean(any(gains[i] <= SLOWDOWN for i in p) for p in picks))
        best = max(max(gains), 0)
        if best < USEFUL:
            continue
        reached = [max(0, max(gains[i] for i in p)) / best for p in picks]
        recovery.append(statistics.mean(reached))
        found.append(statistics.mean(r >= NEAR_BEST for r in reached))
    return statistics.mean(recovery), statistics.mean(found), statistics.mean(harmful), len(recovery)


def analyze(args):
    rows = list(csv.DictReader(open(args.csv)))
    known = json.load(open(os.path.splitext(args.csv)[0] + "-planner.json"))
    runs = defaultdict(dict)
    for r in rows:
        runs[(r["query"], r["index"])][r["pass"]] = math.log(float(r["before_ms"]) / float(r["after_ms"]))
    # Truth is the mean of the passes on a log scale, expressed as the fraction of time saved.
    truth = {k: 1 - math.exp(-statistics.mean(v.values())) for k, v in runs.items()}
    methods = [("At random", random_scores), ("Column-role rule", role_rule_scores),
               ("Postgres planner with HypoPG", planner_scores)]

    sizes = [len(candidates(q)) for q in QUERIES]
    useful = evaluate(random_scores, truth, known, 1)[3]
    print(f"{len(QUERIES)} queries, {len(truth)} query/index pairs ({statistics.mean(sizes):.1f} per query, "
          f"up to {max(sizes)}), {len(rows)} measurements")
    print(f"Queries where the best candidate saves at least half the time: {useful}\n")
    for trials in (1, 2, 3):
        print(f"| Method ({trials} trial{'s' if trials > 1 else ''}) | Share of best saving recovered "
              "| Found a near-best index | A pick 30%+ slower |")
        print("|---|---:|---:|---:|")
        for name, scorer in methods:
            recovery, found, harmful, _ = evaluate(scorer, truth, known, trials)
            print(f"| {name} | {recovery:.0%} | {found:.0%} | {harmful:.1%} |")
        print()

    # Added after the first results, because "share of time saved" is lenient: 99% saved and 91% saved both pass,
    # yet leave query times nine times apart. This compares the times themselves.
    log_gain = {k: statistics.mean(v.values()) for k, v in runs.items()}
    print("Time with the best picked index, against time with the best of all candidates:")
    print("| Method | Trials | Within 1.25x | Median | Worst |")
    print("|---|---:|---:|---:|---:|")
    for name, scorer in methods:
        for trials in (1, 2):
            slower = []
            for query in QUERIES:
                options = candidates(query)
                gains = [log_gain[(query[0], label(c))] for c in options]
                picks = possible_picks(scorer(query, options, known), trials)
                slower.append([math.exp(max(gains) - max(gains[i] for i in p)) for p in picks])
            within = statistics.mean(statistics.mean(r <= 1.25 for r in per_pick) for per_pick in slower)
            # Over every equally likely pick of every query, each query weighted the same.
            weighted = sorted((r, 1 / len(per_pick)) for per_pick in slower for r in per_pick)
            running, median = 0.0, weighted[-1][0]
            for ratio, weight in weighted:
                running += weight
                if running >= len(slower) / 2:
                    median = ratio
                    break
            print(f"| {name} | {trials} | {within:.0%} | {median:.2f}x | {weighted[-1][0]:.0f}x |")
    print()

    print("Per useful query, share of the best saving reached with 2 picks (expected value over ties):")
    print("| Query | Candidates | Best measured | Saved | Rule | Planner |")
    print("|---|---:|---|---:|---:|---:|")
    for query in QUERIES:
        options = candidates(query)
        gains = [truth[(query[0], label(c))] for c in options]
        best = max(gains)
        if best < USEFUL:
            continue
        reach = []
        for scorer in (role_rule_scores, planner_scores):
            picks = possible_picks(scorer(query, options, known), 2)
            reach.append(statistics.mean(max(0, max(gains[i] for i in p)) / best for p in picks))
        print(f"| {query[0]} | {len(options)} | `{label(options[gains.index(best)])}` | {best:.0%} "
              f"| {reach[0]:.0%} | {reach[1]:.0%} |")

    seconds = [v["seconds"] for v in known.values()]
    walls = [float(r["wall_seconds"]) for r in rows]
    print(f"\nAsking the planner about one candidate: median {statistics.median(seconds) * 1000:.0f} ms "
          f"(including starting psql). Measuring one: median {statistics.median(walls):.1f} s.")
    passes = sorted({r["pass"] for r in rows})
    if len(passes) > 1:
        gaps = sorted(abs((1 - math.exp(-v[passes[0]])) - (1 - math.exp(-v[passes[1]]))) for v in runs.values())
        print(f"Difference in saving for the same pair between passes: median {gaps[len(gaps) // 2]:.1%} points, "
              f"90th percentile {gaps[int(len(gaps) * 0.9)]:.1%}")
    # How often the planner's favourite looked cheaper than no index at all but was not faster when run.
    promised = disappointed = 0
    for query in QUERIES:
        options = candidates(query)
        base = json.loads(psql(f"EXPLAIN (FORMAT JSON) {query[1]};"))[0]["Plan"]["Total Cost"] if args.live else None
        if base is None:
            break
        for c in options:
            if known[query[0] + "|" + label(c)]["cost"] < base * 0.5:
                promised += 1
                disappointed += truth[(query[0], label(c))] < 0.2
    if promised:
        print(f"Candidates the planner expected to at least halve the cost: {promised}; "
              f"of those, {disappointed} saved less than 20% of the time.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("setup").set_defaults(action=setup)
    m = commands.add_parser("measure")
    m.add_argument("--passes", type=int, default=2)
    m.add_argument("--reps", type=int, default=9)
    m.add_argument("--seed", type=int, default=7)
    m.add_argument("--out", default="bench/results/hard-study.csv")
    m.set_defaults(action=measure)
    a = commands.add_parser("analyze")
    a.add_argument("csv")
    a.add_argument("--live", action="store_true",
                   help="also compare the planner's promises with measurements; needs the setup database running")
    a.set_defaults(action=analyze)
    args = parser.parse_args()
    args.action(args)


if __name__ == "__main__":
    main()
