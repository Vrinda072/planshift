CREATE TABLE IF NOT EXISTS customers (
    customer_id BIGSERIAL PRIMARY KEY,
    first_name  VARCHAR(50)  NOT NULL,
    last_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(150) NOT NULL UNIQUE,
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    country     VARCHAR(100) NOT NULL,
    signup_date DATE         NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    product_id BIGSERIAL PRIMARY KEY,
    sku        VARCHAR(32)    NOT NULL UNIQUE,
    name       VARCHAR(150)   NOT NULL,
    category   VARCHAR(80)    NOT NULL,
    price      NUMERIC(10, 2) NOT NULL CHECK (price >= 0)
);

-- No index on customer_id here on purpose: this column is the toggle for
-- Experiment 1 (index added vs. index absent). The benchmark engine adds
-- and drops it between baseline and candidate runs.
CREATE TABLE IF NOT EXISTS orders (
    order_id     BIGSERIAL PRIMARY KEY,
    customer_id  BIGINT         NOT NULL REFERENCES customers (customer_id),
    order_date   TIMESTAMP      NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0)
);

-- Postgres does NOT auto-index foreign key columns (only primary keys get
-- an automatic unique index). These two are indexed upfront because they
-- are load-bearing for every join query in the workload and are not part
-- of the customer_id experiment.
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id BIGSERIAL PRIMARY KEY,
    order_id      BIGINT         NOT NULL REFERENCES orders (order_id),
    product_id    BIGINT         NOT NULL REFERENCES products (product_id),
    quantity      INT            NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items (product_id);

CREATE TABLE IF NOT EXISTS experiments (
    experiment_id             BIGSERIAL PRIMARY KEY,
    created_at                TIMESTAMP      NOT NULL DEFAULT now(),
    experiment_type           VARCHAR(50)    NOT NULL,
    dataset_customers         INT            NOT NULL,
    dataset_orders            INT            NOT NULL,
    dataset_seed              BIGINT         NOT NULL,
    repetitions                INT           NOT NULL,
    threshold_fraction        NUMERIC(5, 4)  NOT NULL,
    status                    VARCHAR(20)    NOT NULL,
    overall_percentage_change NUMERIC(10, 2)
);

-- Added after the initial schema; ADD COLUMN IF NOT EXISTS keeps this
-- script idempotent for databases that already have the table (no
-- migration tool like Flyway in this MVP -- schema.sql doubles as one).
ALTER TABLE experiments ADD COLUMN IF NOT EXISTS current_phase VARCHAR(40);

-- target_table/target_column identify what the index experiment operated on.
-- For the built-in workload this is always orders/customer_id; custom
-- query-builder experiments can target any real table/column.
ALTER TABLE experiments ADD COLUMN IF NOT EXISTS target_table VARCHAR(100);
ALTER TABLE experiments ADD COLUMN IF NOT EXISTS target_column VARCHAR(100);

CREATE TABLE IF NOT EXISTS experiment_query_results (
    id                   BIGSERIAL PRIMARY KEY,
    experiment_id        BIGINT         NOT NULL REFERENCES experiments (experiment_id),
    query_id             VARCHAR(80)    NOT NULL,
    query_name           VARCHAR(150)   NOT NULL,
    baseline_median_ms   NUMERIC(12, 4) NOT NULL,
    candidate_median_ms  NUMERIC(12, 4) NOT NULL,
    absolute_change_ms   NUMERIC(12, 4) NOT NULL,
    percentage_change    NUMERIC(10, 2) NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    baseline_plan_json   TEXT,
    candidate_plan_json  TEXT,
    plan_diff_summary    TEXT
);

CREATE INDEX IF NOT EXISTS idx_eqr_experiment_id ON experiment_query_results (experiment_id);
