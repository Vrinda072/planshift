# Postgres with HypoPG, which lets the planner cost an index that doesn't exist. PlanShift uses it for estimates
# when it is there, and bench/hard_study.py uses it as its planner method.
FROM postgres:16
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-hypopg \
    && rm -rf /var/lib/apt/lists/*
