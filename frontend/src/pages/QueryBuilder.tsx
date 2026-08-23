import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { QuerySpec, TableInfo } from "../api/types";
import { ImportDataPanel } from "../components/ImportDataPanel";

const OPERATORS = ["=", "!=", ">", "<", ">=", "<=", "LIKE"];
const AGGREGATES = ["", "COUNT", "SUM", "AVG", "MIN", "MAX"];

export function QueryBuilder() {
  const [tables, setTables] = useState<TableInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const [table, setTable] = useState("");
  const [selectColumns, setSelectColumns] = useState<string[]>([]);
  const [aggregateFunction, setAggregateFunction] = useState("");
  const [aggregateColumn, setAggregateColumn] = useState("");
  const [groupByColumn, setGroupByColumn] = useState("");
  const [filterColumn, setFilterColumn] = useState("");
  const [filterOperator, setFilterOperator] = useState("=");
  const [filterValue, setFilterValue] = useState("");
  const [orderByColumn, setOrderByColumn] = useState("");
  const [orderByDirection, setOrderByDirection] = useState<"ASC" | "DESC">("DESC");
  const [limit, setLimit] = useState(100);
  const [indexColumn, setIndexColumn] = useState("");
  const [repetitions, setRepetitions] = useState(5);
  const [thresholdPercent, setThresholdPercent] = useState(20);

  const [previewSql, setPreviewSql] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);

  const refreshTables = () => api.listTables().then(setTables).catch((e) => setError(e.message));

  useEffect(() => {
    refreshTables();
  }, []);

  const handleImported = (newTableName: string) => {
    refreshTables().then(() => setTable(newTableName));
  };

  const columns = useMemo(
    () => tables?.find((t) => t.tableName === table)?.columns ?? [],
    [tables, table]
  );

  const buildSpec = (): QuerySpec => ({
    table,
    selectColumns: aggregateFunction ? null : selectColumns.length > 0 ? selectColumns : ["*"],
    aggregateFunction: aggregateFunction || null,
    aggregateColumn: aggregateFunction && aggregateFunction !== "COUNT" ? aggregateColumn || null : null,
    groupByColumn: aggregateFunction ? groupByColumn || null : null,
    filterColumn: filterColumn || null,
    filterOperator: filterColumn ? filterOperator : null,
    filterValue: filterColumn ? filterValue : null,
    orderByColumn: orderByColumn || null,
    orderByDirection: orderByColumn ? orderByDirection : null,
    limit,
  });

  const preview = async () => {
    setPreviewError(null);
    setPreviewSql(null);
    try {
      const result = await api.previewQuery(buildSpec());
      setPreviewSql(result.sql);
    } catch (e) {
      setPreviewError((e as Error).message);
    }
  };

  const runExperiment = async () => {
    const effectiveIndexColumn = indexColumn || filterColumn;
    if (!effectiveIndexColumn) {
      setPreviewError("Pick a column to index (or set a filter column, which is used by default).");
      return;
    }
    setRunning(true);
    setPreviewError(null);
    try {
      const result = await api.runQueryBuilderExperiment(
        buildSpec(), effectiveIndexColumn, repetitions, thresholdPercent / 100
      );
      navigate(`/experiments/${result.experimentId}`);
    } catch (e) {
      setPreviewError((e as Error).message);
    } finally {
      setRunning(false);
    }
  };

  const toggleSelectColumn = (col: string) => {
    setSelectColumns((prev) => (prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col]));
  };

  return (
    <div>
      <h1>Query Builder</h1>
      <p className="subtitle">
        Build a query against any table in the connected database -- your own imported dataset included -- by
        picking columns and filters, not typing SQL. Every table and column name is checked against the
        database's real schema before any SQL is generated.
      </p>

      {error && <div className="error-banner">{error}</div>}
      {!error && tables === null && <div className="loading-line">Loading schema...</div>}

      {tables && <ImportDataPanel onImported={handleImported} />}

      {tables && (
        <div className="card">
          <div className="form-row" style={{ flexWrap: "wrap" }}>
            <div className="form-field">
              <label htmlFor="table">Table</label>
              <select
                id="table"
                value={table}
                onChange={(e) => {
                  setTable(e.target.value);
                  setSelectColumns([]);
                  setFilterColumn("");
                  setOrderByColumn("");
                  setAggregateColumn("");
                  setGroupByColumn("");
                  setIndexColumn("");
                  setPreviewSql(null);
                }}
                style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)", minWidth: 160 }}
              >
                <option value="">Select a table...</option>
                {tables.map((t) => (
                  <option key={t.tableName} value={t.tableName}>{t.tableName}</option>
                ))}
              </select>
            </div>
          </div>

          {table && (
            <>
              <div style={{ marginTop: 20 }}>
                <div className="category-tag">Aggregate (optional)</div>
                <div className="form-row" style={{ flexWrap: "wrap", marginTop: 8 }}>
                  <div className="form-field">
                    <label>Function</label>
                    <select
                      value={aggregateFunction}
                      onChange={(e) => setAggregateFunction(e.target.value)}
                      style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}
                    >
                      {AGGREGATES.map((a) => <option key={a} value={a}>{a || "None -- plain select"}</option>)}
                    </select>
                  </div>
                  {aggregateFunction && aggregateFunction !== "COUNT" && (
                    <div className="form-field">
                      <label>On column</label>
                      <select
                        value={aggregateColumn}
                        onChange={(e) => setAggregateColumn(e.target.value)}
                        style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}
                      >
                        <option value="">Choose...</option>
                        {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                      </select>
                    </div>
                  )}
                  {aggregateFunction && (
                    <div className="form-field">
                      <label>Group by (optional)</label>
                      <select
                        value={groupByColumn}
                        onChange={(e) => setGroupByColumn(e.target.value)}
                        style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}
                      >
                        <option value="">None</option>
                        {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                      </select>
                    </div>
                  )}
                </div>
              </div>

              {!aggregateFunction && (
                <div style={{ marginTop: 20 }}>
                  <div className="category-tag">Columns to select (none = all columns)</div>
                  <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 8 }}>
                    {columns.map((c) => (
                      <label key={c.columnName} style={{ display: "flex", alignItems: "center", gap: 5, fontSize: 12.5, color: "var(--text-secondary)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "4px 8px" }}>
                        <input type="checkbox" checked={selectColumns.includes(c.columnName)} onChange={() => toggleSelectColumn(c.columnName)} />
                        {c.columnName}
                      </label>
                    ))}
                  </div>
                </div>
              )}

              <div style={{ marginTop: 20 }}>
                <div className="category-tag">Filter (optional)</div>
                <div className="form-row" style={{ flexWrap: "wrap", marginTop: 8 }}>
                  <div className="form-field">
                    <label>Column</label>
                    <select value={filterColumn} onChange={(e) => setFilterColumn(e.target.value)} style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}>
                      <option value="">None</option>
                      {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                    </select>
                  </div>
                  {filterColumn && (
                    <>
                      <div className="form-field">
                        <label>Operator</label>
                        <select value={filterOperator} onChange={(e) => setFilterOperator(e.target.value)} style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}>
                          {OPERATORS.map((op) => <option key={op} value={op}>{op}</option>)}
                        </select>
                      </div>
                      <div className="form-field">
                        <label>Value</label>
                        <input type="text" value={filterValue} onChange={(e) => setFilterValue(e.target.value)} style={{ width: 140 }} />
                      </div>
                    </>
                  )}
                </div>
              </div>

              <div style={{ marginTop: 20 }}>
                <div className="category-tag">Order &amp; limit</div>
                <div className="form-row" style={{ flexWrap: "wrap", marginTop: 8 }}>
                  <div className="form-field">
                    <label>Order by</label>
                    <select value={orderByColumn} onChange={(e) => setOrderByColumn(e.target.value)} style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}>
                      <option value="">None</option>
                      {aggregateFunction && <option value="result">result (the aggregate)</option>}
                      {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                    </select>
                  </div>
                  {orderByColumn && (
                    <div className="form-field">
                      <label>Direction</label>
                      <select value={orderByDirection} onChange={(e) => setOrderByDirection(e.target.value as "ASC" | "DESC")} style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}>
                        <option value="DESC">DESC</option>
                        <option value="ASC">ASC</option>
                      </select>
                    </div>
                  )}
                  <div className="form-field">
                    <label>Limit</label>
                    <input type="number" min={1} max={5000} value={limit} onChange={(e) => setLimit(Number(e.target.value))} style={{ width: 100 }} />
                  </div>
                </div>
              </div>

              <div style={{ marginTop: 20 }}>
                <button className="btn" onClick={preview}>Preview SQL</button>
              </div>

              {previewSql && (
                <div className="query-sql" style={{ marginTop: 12, background: "var(--bg)", padding: 12, borderRadius: 6, border: "1px solid var(--border-strong)" }}>
                  {previewSql}
                </div>
              )}
              {previewError && <div className="error-banner" style={{ marginTop: 12 }}>{previewError}</div>}
            </>
          )}
        </div>
      )}

      {table && (
        <div className="card" style={{ marginTop: 24 }}>
          <h2>Run as an experiment</h2>
          <p style={{ color: "var(--text-secondary)", marginTop: 0, fontSize: 13 }}>
            Runs the query with the index column absent, then adds an index and runs it again.
          </p>
          <div className="form-row" style={{ flexWrap: "wrap" }}>
            <div className="form-field">
              <label>Index column</label>
              <select
                value={indexColumn}
                onChange={(e) => setIndexColumn(e.target.value)}
                style={{ background: "var(--bg)", border: "1px solid var(--border-strong)", borderRadius: 6, padding: "8px 10px", color: "var(--text-primary)" }}
              >
                <option value="">{filterColumn ? `Default: ${filterColumn}` : "Choose a column..."}</option>
                {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
              </select>
            </div>
            <div className="form-field">
              <label>Repetitions</label>
              <input type="number" min={1} max={20} value={repetitions} onChange={(e) => setRepetitions(Number(e.target.value))} />
            </div>
            <div className="form-field">
              <label>Threshold (%)</label>
              <input type="number" min={1} max={100} value={thresholdPercent} onChange={(e) => setThresholdPercent(Number(e.target.value))} />
            </div>
            <button className="btn btn-primary" onClick={runExperiment} disabled={running}>
              {running ? "Starting..." : "Run Experiment"}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
