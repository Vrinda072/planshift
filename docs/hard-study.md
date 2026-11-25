# Joins and multi-column filters: a rule against Postgres's planner

**Question.** In the [first study](index-study.md) the queries were so simple that choosing was easy. With joins,
several filters and up to 15 candidate indexes per query, how well can two picks (or one) be chosen without running
anything?

**Short answer.** Ask Postgres. Ranking candidates by the planner's own cost, using
[HypoPG](https://github.com/HypoPG/hypopg) to pretend each index exists, found a near-best index with its **first**
pick on all 22 queries. A hand-written column rule did as well on single-table queries but failed on joins, and
overall did worse with two picks than choosing at random.

## Setup

- **Data:** the same copy of the generated e-commerce set and the Uber pickups as the first study, in a throwaway
  Postgres 16 with HypoPG, with fresh statistics.
- **Queries:** 22: 15 on one table with two or three conditions (some with `ORDER BY` and `LIMIT`), and 7 joins.
  They are listed in [`bench/hard_study.py`](../bench/hard_study.py).
- **Candidates:** every index of up to three of the columns a query uses on one table, in every order, leaving out
  primary keys and indexes that already exist. 136 pairs, 6.2 per query, at most 15.
- **Measurement:** Postgres's execution time, 9 runs per side [taking turns](../README.md#how-noisy-are-the-timings),
  twice, in a different random order each pass: 272 measurements. The same pair differed between passes by a median
  of 0.6 percentage points of time saved (4.4 for one pair in ten).
- **Fixed in advance:** queries, candidate rule, both ranking methods and the scoring, which matches the first study.
  Before measuring, two join queries that turned out to have only one candidate each were removed, since every
  method would have got them for free, and three-column indexes were added to make guessing harder.

## Methods

| Method | How it ranks |
|---|---|
| At random | The floor |
| Column-role rule | Lead with an equality column, then a join, range or ordering column; follow with another equality column, then ordering, range, join. It knows nothing about tables or how selective anything is |
| Planner with HypoPG | Lowest estimated cost of the query with the hypothetical index; ties go to the smaller index |

## Results

Every query was worth indexing (the best candidate saved at least half the time on all 22).

| Method | Trials | Share of best saving recovered | Found a near-best index |
|---|---:|---:|---:|
| At random | 1 | 70% | 55% |
| Column-role rule | 1 | 83% | 77% |
| Planner with HypoPG | 1 | 100% | 100% |
| At random | 2 | 89% | 82% |
| Column-role rule | 2 | 84% | 77% |
| Planner with HypoPG | 2 | 100% | 100% |

"Share of time saved" is lenient: saving 99% and saving 91% both pass, yet leave query times nine times apart. So
this second view, **added after seeing the first results**, compares the times themselves: how much slower is the
query with the picked index than with the best candidate?

| Method | Trials | Within 1.25x of the best | Median | Worst |
|---|---:|---:|---:|---:|
| At random | 1 | 32% | 2.50x | 1342x |
| At random | 2 | 57% | 1.06x | 169x |
| Column-role rule | 1 | 66% | 1.00x | 33x |
| Column-role rule | 2 | 73% | 1.00x | 33x |
| Planner with HypoPG | 1 | 95% | 1.00x | 2x |
| Planner with HypoPG | 2 | 95% | 1.00x | 2x |

**Where the rule fails.** On the 15 single-table queries its two picks matched the planner's (98% or more on each).
All of the gap is in the joins, where it reached as little as 6% of the best saving. It ranks columns by how they
are used and has no idea which table is worth indexing: in `customers JOIN orders` filtered on a customer's country
and on a recent order date, the index that matters is on the date (35x faster), and the rule's favourites are on the
other side of the join (about 1.1x).

**Why the planner does so well.** HypoPG changes nothing but which indexes the planner believes in, so its ranking
uses the same cost model that later chooses the plan. Of 56 candidates it expected to at least halve a query's cost,
none saved less than 20% of the time. Asking about one candidate took a median of 49 ms, including starting `psql`,
against 0.7 s to measure it.

## What this does not show

- **This is close to the best case for the planner.** Most of the data is generated with evenly spread, independent
  columns, and Postgres estimates row counts by assuming columns are independent. On real data with correlated or
  badly skewed columns its estimates are often wrong, and that is where measuring would earn its keep. Only 3 of the
  22 queries use real data.
- Fresh statistics, data that fits in memory, one machine, no `GROUP BY` or subqueries, and 22 queries written by one
  author.
- Indexes were tried one at a time. Choosing a *set* of indexes for a workload, and what they cost on writes, is a
  different and harder problem.

**What follows for PlanShift.** Where HypoPG can be installed, planner cost is a far better way to decide which
candidates are worth measuring than PlanShift's own estimate or a rule, and the measurement is then the check on
the planner. PlanShift does not do this yet. The study worth running next uses correlated, skewed real data, to find
where the planner's first pick stops being right.

## Reproduce

Needs the data from the [first study](index-study.md#reproduce) loaded.

```bash
python3 bench/hard_study.py setup      # builds Postgres with HypoPG and copies the data into it
python3 bench/hard_study.py measure
python3 bench/hard_study.py analyze bench/results/hard-study.csv --live
docker rm -f planshift-hypopg
```

Raw results are in [`bench/results/hard-study.csv`](../bench/results/hard-study.csv), and what the planner said
beforehand in [`bench/results/hard-study-planner.json`](../bench/results/hard-study-planner.json).
