# Postgres with HypoPG (hypothetical indexes), only for the planner baseline in hard_study.py.
FROM postgres:16
RUN apt-get update \
    && apt-get install -y --no-install-recommends postgresql-16-hypopg \
    && rm -rf /var/lib/apt/lists/*
