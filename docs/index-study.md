# Can PlanShift pick which two indexes to try?

**Question.** Given several candidate indexes and time to benchmark only two, can PlanShift choose ones that deliver
most of the gain found by testing them all?

**Short answer.** Yes, but mostly because the choice is easy here. A one-line rule (try the filter column, then the
filter column followed by the other column the query uses) recovered 99% of the best saving. Picking two at random
already recovered 90%. A learned ranker was ahead of the rule by a single query out of 19, which is not enough to
justify it. At this data size, measuring all the candidates takes about four seconds. A
[second study](hard-study.md) tries joins and several filters, where choosing is harder.

## Setup

- **Data:** the built-in generated e-commerce set (`orders` 500k rows, `order_items` 1.5M, `customers` 100k) and
  564,516 [Uber pickups](https://github.com/fivethirtyeight/uber-tlc-foil-response) from April 2014.
- **Queries:** 26 single-table queries with one filter and optionally an `ORDER BY`, `LIMIT` or `COUNT`, written
  before any of them were measured. They are listed in [`bench/index_study.py`](../bench/index_study.py).
- **Candidates:** for each query, by a fixed rule: the filter column, an unrelated column, the ordering column if
  there is one, and the filter column and its partner in both orders. 112 query and index pairs in all, 4.3 per query.
- **Measurement:** each pair through PlanShift itself, 9 runs per side in the "in blocks" order, twice, in a
  different random order each pass: 224 experiments. Postgres 16 in Docker on a laptop.
- **Scoring:** the true value of a pair is the mean of its two passes (on a log scale). A query is *useful* if its
  best candidate saves at least 50% of the time (19 of 26). A pick is *near-best* at 90% of the best saving. A pick
  is *harmful* if it makes the query at least 30% slower. Ties are averaged over every way of breaking them. These
  definitions are in [`bench/analyze_study.py`](../bench/analyze_study.py) and were written before the results.

## Methods compared

| Method | How it picks |
|---|---|
| Two at random | The floor |
| PlanShift estimator, no history | Rules out indexes whose first column can't serve the query, then ranks by a rule of thumb from the filter's selectivity |
| PlanShift estimator, with history | The same, but using past runs: all of them from the *other* queries, none from the one being ranked (this and the family split below were added after the first results) |
| Filter-column rule | The filter column, then (filter column, ordering or second column) |
| Learned ranker | Ridge regression on features of the index and query (which column leads, filter type, selectivity, whether the order is covered, table size). Tested only on a table, a whole dataset, or a family of queries (same kind of filter, ordering and counting; six families) that it was not trained on |

## Results

19 useful queries (the figures are the same with the bar at 30%, which adds one query):

| Method (2 trials) | Share of best saving recovered | Found a near-best index | First pick 30%+ slower | Either pick 30%+ slower |
|---|---:|---:|---:|---:|
| Two at random | 90% | 88% | 3.7% | 6.7% |
| PlanShift estimator, no history | 99% | 95% | 3.8% | 3.8% |
| PlanShift estimator, history from the other queries | 100% | 100% | 3.8% | 3.8% |
| Filter-column rule | 99% | 95% | 3.8% | 3.8% |
| Learned ranker, held-out table | 100% | 100% | 3.8% | 3.8% |
| Learned ranker, held-out dataset | 100% | 100% | 3.8% | 3.8% |
| Learned ranker, held-out query family | 100% | 100% | 3.8% | 7.7% |

The last two columns cover all 26 queries.

**What history adds.** It barely changes which indexes get picked, but it makes the predicted number much better:
against the measured change, the estimator's median error over all pairs fell from 43 percentage points with no
history to 11 with it. This is generous to the estimator: its history holds every candidate of 25 other queries,
measured on the same machine and data, which is more than a new user would have.

**Why random does so well.** For a useful query, 2.6 of its 4.4 candidates are near-best on average, so two draws
rarely miss all of them.

**The one query that separates the methods** is O5: `total_amount > 6000 ORDER BY order_date DESC LIMIT 50`. The
winner was `(order_date, total_amount)` at 99% saved, led by the *ordering* column: Postgres walks the index newest
first and stops after 50 matches. The rule and the estimator both started from the filter column and got 84%.

**Some candidates do harm.** Three queries had an index that made them clearly slower: an index on `unit_price`
made a broad `unit_price` range query 84% slower, and an index on `lon` made a query that filters on `lat` and sorts
by `lon` more than twice as slow. In the second case Postgres read the index in `lon` order and threw rows away
until it had found 20 with the right `lat`, which was more work than the scan it replaced.

**Cost.** The best indexes ranged from under 1 MB to 45 MB and took 40 ms to about 1 s to build. Composite indexes
were often only slightly better than the single column and noticeably larger. An estimate took a median of 3 ms per
candidate, against 1 s to measure one.

**Repeatability.** The pass-1 winner was still near-best in pass 2 for all 19 useful queries. The saving for the
same pair differed by a median of 2.6 percentage points between passes, but by more than 22 points for one pair in
ten. Of 84 runs where the plan did not change, 10 still moved by more than 20% and 5 were reported as a change,
which matches the noise measured by the [A/A checks](../README.md#how-noisy-are-the-timings).

## What this does not show

- 26 queries, written by one person who knows how B-trees work, is a small sample. A one-query gap between methods
  is not a real difference, and the ranker's features encode that same knowledge.
- Single-table queries with one filter only: no joins or multiple predicates. The [second study](hard-study.md)
  covers those, and compares against Postgres's own planner using hypothetical indexes (HypoPG), which was the obvious
  method missing here.
- One machine, data that fits in memory, and no measurement of what indexes cost on writes.

## Reproduce

```bash
docker compose up -d --build
# generates the data, then keeps running on a spare port: stop it with Ctrl+C once it logs that it has finished
(cd backend && mvn spring-boot:run -Dspring-boot.run.arguments="--generate-data --scale=full --server.port=0")
curl -LO https://raw.githubusercontent.com/fivethirtyeight/uber-tlc-foil-response/master/uber-trip-data/uber-raw-data-apr14.csv
curl -F file=@uber-raw-data-apr14.csv -F tableName=uber_pickups http://localhost:8080/api/datasets/import
python3 bench/index_study.py
python3 bench/analyze_study.py bench/results/study.csv
```

Raw results are in [`bench/results/study.csv`](../bench/results/study.csv). Timings will differ on other machines.
