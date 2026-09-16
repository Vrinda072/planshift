# PLANSHIFT

[![CI](https://github.com/Vrinda072/planshift/actions/workflows/ci.yml/badge.svg)](https://github.com/Vrinda072/planshift/actions/workflows/ci.yml)

Database performance, without the guesswork.

PLANSHIFT runs a fixed SQL workload against two PostgreSQL configurations,
times the difference, captures the execution plan for each run, and flags
which queries actually regressed. It's meant to answer "did adding that
index help?" with numbers instead of a guess.

**Live:** [frontend](https://frontend-mauve-two-60.vercel.app) ·
[backend health check](https://planshift-backend.onrender.com/api/health)
(the backend is a REST API with no UI of its own — that link is just proof
it's up). Free-tier hosting, so the backend can take ~30s to wake up on
the first request after it's been idle.

## Why

"Just add an index" gets thrown around a lot. This is the tool I wanted for
checking that claim: same data, same queries, before and after, with the
query plan showing exactly what the planner did differently.

## Architecture

```
React (TypeScript, Vite)  ──HTTP──▶  Spring Boot REST API  ──JDBC──▶  PostgreSQL
     port 5173                          port 8080                    port 5432
```

Backend package layout (`backend/src/main/java/com/planshift/`):

| Package | Responsibility |
|---|---|
| `datagen` | Deterministic synthetic dataset generation, seeded `Random` |
| `workload` | The fixed catalog of 10 benchmark queries |
| `benchmark` | Times query execution, repeated and median-aggregated |
| `queryplan` | Captures `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`, parses it into a tree, diffs two trees |
| `experiment` | Baseline-vs-candidate orchestration, async with real progress phases, regression classification, persistence |
| `schema` | Live introspection via `information_schema` — backs the query builder and validates every identifier before it reaches SQL |
| `querybuilder` | Structured table/columns/filter spec → parameterized SQL, no raw SQL accepted |
| `dataimport` | CSV → Postgres table via `COPY`, with per-column type inference |
| `controller` | REST API |

No microservices, no message queue, no ORM — a JDBC-backed Spring Boot API
and a static React frontend. The problem didn't need more than that.

## How it works

**Dataset.** A synthetic e-commerce schema — customers, products, orders,
order_items — generated from a fixed seed, so re-running the generator
produces identical data. `DataGeneratorIT` checks this directly: two
generations, same seed, same checksum.

**Workload.** Ten read-only queries covering selective and non-selective
filters, date ranges, joins, aggregation, `GROUP BY`, `ORDER BY`, and
multi-table joins. Neither the API nor the UI accepts arbitrary SQL against
this dataset.

**Running an experiment.** The supported change is adding an index — on
`orders.customer_id` for the built-in workload, or on any column for a
custom query (see Query Builder below). An experiment runs the workload
with the index absent, then present, five times per query, and takes the
median as the representative time. A single timing sample is mostly noise;
five runs and a median cut through most of it.

One subtlety cost me a real bug: running baseline immediately followed by
candidate lets whichever phase runs second inherit a warm cache from the
first, making it look faster regardless of the index. Each phase now runs
an untimed pass over the workload first to warm up on its own terms —
`IndexExperimentService.warmUp`.

**Regression detection.** `percentage_change = (candidate - baseline) /
baseline * 100`. A configurable threshold, 20% by default, sorts each query
into improved, unchanged, or regressed.

**Plan comparison.** Plans are parsed into a simplified tree and diffed
with a recursive positional walk, not full tree-edit-distance — these
trees are shallow enough that the simpler approach is correct and a lot
easier to read. See the comment in `PlanComparator` for the reasoning.

**Progress.** `POST /api/experiments` returns immediately with the
experiment in `RUNNING` state. The work runs on a background thread (Java
21 virtual threads), updating a `current_phase` column at each step —
dropping the index, warming up, measuring, adding the index, comparing.
The frontend polls and shows whichever phase is actually running.

**Query Builder.** The built-in dataset and workload are one fixed
example. Query Builder works against any table: pick columns, an optional
filter, order, and limit, and the generated SQL is shown before anything
runs. Every table and column name is checked against `information_schema`
before touching SQL — no raw SQL box, and the one user-supplied value (a
filter value) is always a bound parameter. Import a CSV first to point it
at your own data; Postgres's own `COPY` parses and loads it, with a type
inferred per column.

**Predicting impact before running the real experiment.** A real
experiment is slow on purpose — it actually drops and re-adds an index and
times both sides. "Predict Impact" gives a cheaper answer first: a plain
`EXPLAIN` (nothing executes) gets the planner's own row estimate for the
query, turned into a selectivity ratio, which is compared against every
past *completed* experiment with a similar selectivity — close matches get
averaged into a prediction. Below three matches, it falls back to a
selectivity-only heuristic and says so explicitly, rather than presenting
a guess as a measurement.

## Example: the built-in workload

100,000 customers, 500,000 orders, 1.5M order_items, Postgres 16, via
docker-compose:

| Query | Baseline (median of 5) | Candidate (median of 5) | Change | Status |
|---|---:|---:|---:|---|
| Selective customer lookup | 0.390 ms | 0.227 ms | -41.7% | IMPROVED |
| Non-selective customer lookup | 47.105 ms | 43.735 ms | -7.2% | UNCHANGED |
| Order history lookup | 8.562 ms | 0.260 ms | -97.0% | IMPROVED |
| Date-range query | 56.049 ms | 39.632 ms | -29.3% | IMPROVED |
| Join query | 247.536 ms | 219.605 ms | -11.3% | UNCHANGED |
| Aggregation | 12.811 ms | 13.172 ms | +2.8% | UNCHANGED |
| Group by order status | 18.978 ms | 18.538 ms | -2.3% | UNCHANGED |
| Order by total value | 14.419 ms | 14.408 ms | -0.1% | UNCHANGED |
| Product lookup | 0.816 ms | 0.668 ms | -18.1% | UNCHANGED |
| Multi-table revenue by category | 42.404 ms | 46.558 ms | +9.8% | UNCHANGED |

Overall: -9.2%.

The order-history query — filtered on `customer_id`, the column that gets
indexed — improved 97%. The plan explains why:

```
BEFORE (no index):                    AFTER (with index):
Sort                                  Sort
 └─ Gather                             └─ Bitmap Heap Scan on orders
     └─ Seq Scan on orders                 └─ Bitmap Index Scan
        Filter: (customer_id = 500)           via idx_orders_customer_id
        (parallel workers scan the             Index Cond: (customer_id = 500)
         full table and combine results)
```

Worth noting: Postgres didn't pick a plain Index Scan. It used a Bitmap
Index Scan combined with a Bitmap Heap Scan, and the baseline was a
*parallel* sequential scan (`Gather`), not a simple one — the table's large
enough that the planner judged parallelism worthwhile. I didn't simplify
this for the writeup; it's what actually ran.

Every other query, none of which filter on `customer_id`, landed at
`UNCHANGED` — single-digit swings from ordinary system noise, inside the
20% threshold.

A smaller early run (1,000 customers) showed every query "improving,"
which turned out to be the cache-warming bug above, not the index. Kept
here because catching your own benchmark's confound is a more useful
result than pretending it didn't happen.

## Example: a custom dataset

A 3-row CSV (`title, year, rating, genre`) imported through the UI:
Postgres inferred `year` as `BIGINT`, `rating` as `DOUBLE PRECISION`,
`title`/`genre` as `TEXT`. Built a query filtering on genre, ran it as an
experiment indexing that column:

```
Custom query on kaggle_movies: 0.1812 ms -> 0.3073 ms  (+69.6%)  REGRESSED
```

Correct result for data this small — an index on a 3-row table is pure
overhead with nothing to look up. It's also decent evidence the detector
isn't wired to say "faster" by default: it reports what happens, including
when that's a regression.

## Testing

32 backend tests, plus 16 frontend tests (Vitest + React Testing Library,
covering the format helpers and a couple of components). Backend unit
tests (`RegressionDetectorTest`, `PlanParserTest`, `PlanComparatorTest`,
`HealthControllerTest`, `QueryBuilderServiceTest`) cover pure logic,
including feeding `"orders; DROP TABLE orders; --"` in as a table name and
checking it gets rejected. Integration tests (`DataGeneratorIT`,
`IndexExperimentIT`, `CsvImportServiceIT`) run against a disposable
Postgres container via Testcontainers: reproducibility, referential
integrity, the full experiment pipeline end to end, and CSV import through
real `COPY`, including a quoted field with a comma in it.

One of them caught a real flake: `orderHistoryQueryImprovesWithIndex`
asserted `candidate <= baseline`, which at small scale is sub-millisecond
and noisy enough to occasionally fail on nothing but timing jitter.
Replaced with an assertion on the plan itself — the scan strategy changed
— which is deterministic.

```bash
cd backend
mvn test      # unit tests
mvn verify    # unit + integration tests, needs Docker running

cd frontend
npm test
```

## Running it

Requires Docker and Docker Compose.

```bash
git clone https://github.com/Vrinda072/planshift.git
cd planshift
cp .env.example .env
docker compose up -d --build
```

`.env` holds the Postgres credentials (`docker-compose.yml` reads it via
`env_file:`) and isn't committed. The defaults in `.env.example` work fine
for local use as-is; change them if you're running this anywhere other
people can reach it.

Postgres on 5432, the API on 8080, the frontend on 5173 by default. Any of
these can collide with something already running on your machine --
`POSTGRES_HOST_PORT`, `BACKEND_HOST_PORT`, and `FRONTEND_HOST_PORT` in
`.env` remap them. If you change `BACKEND_HOST_PORT`, rebuild
(`docker compose up -d --build`, not just `up -d`) -- the frontend has the
API URL baked in at build time, not read at runtime.

The database starts empty:

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data --scale=full"
```

`--scale=full` is the fixed 100k/500k preset used above; anything else
(including omitting `--scale`) uses the `DATASET_CUSTOMERS`/`DATASET_PRODUCTS`/
`DATASET_ORDERS`/`DATASET_MAX_ITEMS_PER_ORDER`/`DATASET_SEED` values in
`.env`, 1,000 customers by default -- fast, for local iteration. Either
way, `--customers`/`--products`/`--orders`/`--max-items`/`--seed` override
individual fields for a one-off size without touching `.env` or `--scale=full`:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data --scale=full --customers=200000"
```

Or skip the built-in dataset and use Query Builder to import your own CSV.

Then hit "Run Experiment" in the UI, or:

```bash
curl -X POST http://localhost:8080/api/experiments \
  -H "Content-Type: application/json" \
  -d '{"repetitions": 5, "thresholdFraction": 0.20}'
```

### Local development, without full rebuilds

```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run     # :8080
cd frontend && npm install && npm run dev   # :5173
```

## API

| Endpoint | Description |
|---|---|
| `GET /api/health` | App and DB connectivity check |
| `GET /api/queries` | The 10-query workload catalog |
| `GET /api/experiments` | All past experiments, summary |
| `GET /api/experiments/{id}` | Full detail incl. plan JSON; poll while `RUNNING` for live phase |
| `POST /api/experiments` | Starts a baseline-vs-candidate experiment (202, async) |
| `GET /api/regressions` | Every regressed query result, across experiments |
| `GET /api/schema/tables` | Table/column introspection for the query builder |
| `POST /api/query-builder/preview` | Validates a query spec and returns the generated SQL |
| `POST /api/query-builder/run` | Runs an experiment against a custom table/query (202, async) |
| `POST /api/query-builder/predict-impact` | Estimates the result without running the real experiment |
| `POST /api/datasets/import` | Multipart CSV upload → new Postgres table via `COPY` |

## Limitations

- Only index add/remove is supported as a database change — any table or
  column now, but still just indexing.
- Predict Impact's historical lookup is only as good as the experiment
  history it draws from. On a fresh database it has nothing to compare
  against and always falls back to the heuristic.
- Small tables are noisy — sub-millisecond timings are mostly measurement
  jitter. The 100k/500k run above is signal; the `kaggle_movies` result is
  a demonstration of the noise floor, not a counterexample.
- CSV import reads the whole file into memory and scans every row for type
  inference before `COPY`. Fine at Kaggle-CSV scale (capped at 50MB);
  would stream for anything larger.
- No repeated-experiment statistics — confidence intervals across separate
  runs, not just within one — and no concurrency testing.

## Where this could go

Predict Impact is a nearest-neighbor lookup, not a trained model — the
next step is an actual regression fit over accumulated experiment history
once there's enough of it, and the original open question is still open:
do execution-plan features predict regressions well enough to replace the
selectivity heuristic entirely?

Also worth doing: server-sent events instead of polling for experiment
progress (the backend already knows the phase the moment it changes), and
moving the Overview page's stats to a real `/api/experiments/summary`
endpoint instead of computing them client-side from the full list.

## Stack

Java 21, Spring Boot 3.5, Maven, PostgreSQL 16, JDBC, JUnit 5,
Testcontainers, React, TypeScript, Vite, Docker Compose.
