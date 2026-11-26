# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

A developer with a slow query who wants to know whether an index will actually help before adding it. They know SQL,
and may not be fluent in reading query plans.

PlanShift is also a portfolio project that recruiters and reviewers will open, but the app is designed for the
developer: what shows well is a tool that does its job.

## Product Purpose

PlanShift answers "does adding that index actually help?" by measuring it. It times a query without and with a
temporary index, removes the index again, and compares the plans Postgres used. Success is a developer leaving with an
answer they can trust (faster, slower or no real difference), why (what changed in the plan), what it costs (size and
build time), and how far to believe it.

## Positioning

It runs the real before and after, keeps every individual timing, and says when a difference can't be trusted. It has
been tested against its own noise, and the places where its estimates fail are published next to the places they work.

## Operating Context

- Pick a table and query (built-in e-commerce workload, or any table including an imported CSV), choose one or
  several index columns, then estimate or run.
- An estimate uses Postgres's planner with a hypothetical index where HypoPG is available. It builds nothing.
- An experiment is queued, runs one at a time, and takes seconds or longer; the UI shows the real phase.
- Results are compared across candidate indexes, past experiments and regressions, and can be exported as CSV or JSON.
- Public demo on free hosting: Vercel (frontend), Render (API), Neon (Postgres). The first request after a quiet spell
  can take about 30 s. There is no sign-in.

## Capabilities and Constraints

- Single-table queries with one filter, optional ordering, aggregate and limit from the query builder; a 10-query
  built-in workload that includes joins. No raw SQL is accepted.
- Indexes of one or several columns (btree).
- Run order: taking turns (default) or in blocks. A/A checks show how much timings vary with nothing changed.
- Results always say which clock produced the timings. The live demo uses Postgres's execution time.
- Keeping a tested index is off by default and off on the public demo. The `CREATE INDEX` statement is always offered
  to copy.
- Must keep working on the free Vercel, Render and Neon plans, which includes not polling the database while idle.
- Terms used in the product: experiment, candidate index, before and after, A/A check, taking turns, in blocks.

## Brand Commitments

- Name: PlanShift, set as "planshift" in the app's top navigation.
- The look should be fun and use more color. This replaces the earlier minimal white-and-black direction, which is no
  longer a commitment.
- Visual changes are shown as mockups before they are built.
- Voice: plain and specific. No filler, and nothing that reads as machine-written marketing.

## Evidence on Hand

- [Joins and multi-column filters](docs/hard-study.md) and [single-filter queries](docs/index-study.md), with raw
  results in `bench/results/`.
- Noise measurements by run order, in the README and `bench/results/run-order.csv`.
- `docs/demo.gif` and `docs/architecture.svg`.
- A live demo with real experiments on generated e-commerce data; the README's example uses public Uber pickup data.
- There are no users, testimonials, customers or production deployments to cite. Do not invent them.

## Product Principles

1. Measure, then claim. Every number shown or written comes from a real run or the planner, and says which.
2. Show how far to trust a result next to the result, including when the honest answer is "no real difference".
3. State limits where the claim is made, not somewhere a reader won't look.
4. Leave the database as it was found. Nothing a visitor does through an experiment lasts.

## Accessibility & Inclusion

WCAG 2.1 AA: contrast, full keyboard use and screen-reader support. Since the product reports "faster" and "slower",
color must never be the only thing that carries that meaning.
