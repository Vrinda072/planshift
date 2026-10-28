import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import type { PredictedImpact } from "../api/client";
import type { QuerySpec, TableInfo } from "../api/types";
import { AnimatedNumber } from "../components/AnimatedNumber";
import { CopyButton } from "../components/CopyButton";
import { ImportDataPanel } from "../components/ImportDataPanel";
import { PageHeader } from "../components/PageHeader";
import { useDocumentTitle } from "../hooks/useDocumentTitle";
import { changeClassName, formatChangeText } from "../format";

const OPERATORS = ["=", "!=", ">", "<", ">=", "<=", "LIKE"];
const AGGREGATES = ["", "COUNT", "SUM", "AVG", "MIN", "MAX"];

export function QueryBuilder() {
  const [tables, setTables] = useState<TableInfo[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  useDocumentTitle("Query builder");

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
  const [indexColumns, setIndexColumns] = useState<string[]>([]);
  const [repetitions, setRepetitions] = useState(5);
  const [thresholdPercent, setThresholdPercent] = useState(20);

  const [previewSql, setPreviewSql] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [running, setRunning] = useState(false);
  const [predictedImpact, setPredictedImpact] = useState<PredictedImpact | null>(null);
  const [predicting, setPredicting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const refreshTables = () => api.listTables().then(setTables).catch((e) => setError(e.message));

  useEffect(() => {
    refreshTables();
  }, []);

  const handleImported = (newTableName: string) => {
    refreshTables().then(() => setTable(newTableName));
  };

  const tableInfo = useMemo(() => tables?.find((t) => t.tableName === table) ?? null, [tables, table]);
  const columns = useMemo(() => tableInfo?.columns ?? [], [tableInfo]);
  const indexedNames = useMemo(() => columns.filter((c) => c.indexed).map((c) => c.columnName), [columns]);

  const spec: QuerySpec = useMemo(
    () => ({
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
    }),
    [table, selectColumns, aggregateFunction, aggregateColumn, groupByColumn, filterColumn, filterOperator,
      filterValue, orderByColumn, orderByDirection, limit]
  );

  // The SQL panel regenerates as the form changes (debounced), so there is no
  // separate "preview" step. The server still validates and builds the query.
  useEffect(() => {
    if (!table) return;
    const handle = setTimeout(() => {
      api
        .previewQuery(spec)
        .then((result) => {
          setPreviewSql(result.sql);
          setPreviewError(null);
        })
        .catch((e) => {
          setPreviewSql(null);
          setPreviewError((e as Error).message);
        });
    }, 250);
    return () => clearTimeout(handle);
  }, [spec, table]);

  const effectiveIndexColumns = indexColumns.length > 0 ? indexColumns : filterColumn ? [filterColumn] : [];
  const alreadyIndexed = columns.some((c) => c.columnName === effectiveIndexColumns[0] && c.indexed);

  const toggleIndexColumn = (col: string) => {
    setPredictedImpact(null);
    setIndexColumns((prev) => (prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col]));
  };

  const predictImpact = async () => {
    if (effectiveIndexColumns.length === 0) {
      setActionError("Pick a column to index, or set a filter column (it is used by default).");
      return;
    }
    setPredicting(true);
    setActionError(null);
    setPredictedImpact(null);
    try {
      setPredictedImpact(await api.predictImpact(spec, effectiveIndexColumns));
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setPredicting(false);
    }
  };

  const runExperiment = async () => {
    if (effectiveIndexColumns.length === 0) {
      setActionError("Pick a column to index, or set a filter column (it is used by default).");
      return;
    }
    setRunning(true);
    setActionError(null);
    try {
      const result = await api.runQueryBuilderExperiment(spec, effectiveIndexColumns, repetitions, thresholdPercent / 100);
      navigate(`/experiments/${result.experimentId}`);
    } catch (e) {
      setActionError((e as Error).message);
    } finally {
      setRunning(false);
    }
  };

  const toggleSelectColumn = (col: string) => {
    setSelectColumns((prev) => (prev.includes(col) ? prev.filter((c) => c !== col) : [...prev, col]));
  };

  const sqlShown = table ? previewSql : null;

  return (
    <div>
      <PageHeader
        title="Query builder"
        sub="Build a query against any table, import your own data, then measure what an index does to it. Table and column names are checked against the live schema before any SQL is generated."
      />

      {error && <div className="error-banner">{error}</div>}
      {!error && tables === null && <div className="loading-line">Loading schema...</div>}

      {tables && (
        <div className="qb">
          <section className="panel">
            <div className="step">
              <div className="step-head">
                <span className="step-title">Dataset</span>
              </div>
              <div className="form-field" style={{ marginBottom: 16 }}>
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
                    setIndexColumns([]);
                    setPreviewSql(null);
                    setPredictedImpact(null);
                  }}
                  style={{ minWidth: 220 }}
                >
                  <option value="">Select a table...</option>
                  {tables.map((t) => (
                    <option key={t.tableName} value={t.tableName}>{t.tableName} ({t.rowEstimate.toLocaleString()} rows)</option>
                  ))}
                </select>
              </div>
              {tableInfo && (
                <p className="table-facts">
                  <span className="tnum">{tableInfo.rowEstimate.toLocaleString()}</span> rows,{" "}
                  <span className="tnum">{tableInfo.columns.length}</span> columns.{" "}
                  {indexedNames.length > 0 ? (
                    <>Already indexed: <span className="mono">{indexedNames.join(", ")}</span>.</>
                  ) : (
                    "No indexes besides the ones PLANSHIFT manages."
                  )}
                </p>
              )}
              <ImportDataPanel onImported={handleImported} />
            </div>

            {table && (
              <>
                <div className="step fade-section">
                  <div className="step-head">
                    <span className="step-title">Select</span>
                    <span className="step-opt">columns, or aggregate</span>
                  </div>
                  <div className="form-row" style={{ marginBottom: aggregateFunction ? 0 : 14 }}>
                    <div className="form-field">
                      <label>Aggregate</label>
                      <select value={aggregateFunction} onChange={(e) => setAggregateFunction(e.target.value)}>
                        {AGGREGATES.map((a) => (
                          <option key={a} value={a}>{a || "None"}</option>
                        ))}
                      </select>
                    </div>
                    {aggregateFunction && aggregateFunction !== "COUNT" && (
                      <div className="form-field">
                        <label>Of column</label>
                        <select value={aggregateColumn} onChange={(e) => setAggregateColumn(e.target.value)}>
                          <option value="">Choose...</option>
                          {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                        </select>
                      </div>
                    )}
                    {aggregateFunction && (
                      <div className="form-field">
                        <label>Group by</label>
                        <select value={groupByColumn} onChange={(e) => setGroupByColumn(e.target.value)}>
                          <option value="">None</option>
                          {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                        </select>
                      </div>
                    )}
                  </div>
                  {!aggregateFunction && (
                    <>
                      <div className="label" style={{ marginBottom: 8 }}>Columns (none selected means all)</div>
                      <div className="chips">
                        {columns.map((c) => (
                          <label key={c.columnName} className="chip">
                            <input type="checkbox" checked={selectColumns.includes(c.columnName)} onChange={() => toggleSelectColumn(c.columnName)} />
                            {c.columnName}
                            {c.indexed && <span className="chip-idx" title="Already indexed">idx</span>}
                          </label>
                        ))}
                      </div>
                    </>
                  )}
                </div>

                <div className="step fade-section" style={{ animationDelay: "50ms" }}>
                  <div className="step-head">
                    <span className="step-title">Filter</span>
                    <span className="step-opt">optional</span>
                  </div>
                  <div className="form-row">
                    <div className="form-field">
                      <label>Column</label>
                      <select value={filterColumn} onChange={(e) => setFilterColumn(e.target.value)}>
                        <option value="">None</option>
                        {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                      </select>
                    </div>
                    {filterColumn && (
                      <>
                        <div className="form-field">
                          <label>Operator</label>
                          <select value={filterOperator} onChange={(e) => setFilterOperator(e.target.value)}>
                            {OPERATORS.map((op) => <option key={op} value={op}>{op}</option>)}
                          </select>
                        </div>
                        <div className="form-field">
                          <label>Value</label>
                          <input type="text" value={filterValue} onChange={(e) => setFilterValue(e.target.value)} style={{ width: 160 }} />
                        </div>
                      </>
                    )}
                  </div>
                </div>

                <div className="step fade-section" style={{ animationDelay: "100ms" }}>
                  <div className="step-head">
                    <span className="step-title">Order and limit</span>
                    <span className="step-opt">optional</span>
                  </div>
                  <div className="form-row">
                    <div className="form-field">
                      <label>Order by</label>
                      <select value={orderByColumn} onChange={(e) => setOrderByColumn(e.target.value)}>
                        <option value="">None</option>
                        {aggregateFunction && <option value="result">result (the aggregate)</option>}
                        {columns.map((c) => <option key={c.columnName} value={c.columnName}>{c.columnName}</option>)}
                      </select>
                    </div>
                    {orderByColumn && (
                      <div className="form-field">
                        <label>Direction</label>
                        <select value={orderByDirection} onChange={(e) => setOrderByDirection(e.target.value as "ASC" | "DESC")}>
                          <option value="DESC">Descending</option>
                          <option value="ASC">Ascending</option>
                        </select>
                      </div>
                    )}
                    <div className="form-field">
                      <label>Limit</label>
                      <input type="number" min={1} max={5000} value={limit} onChange={(e) => setLimit(Number(e.target.value))} style={{ width: 96 }} />
                    </div>
                  </div>
                </div>
              </>
            )}
          </section>

          <aside className="qb-aside">
            <section className="panel">
              <div className="panel-head">
                <h2 className="panel-title">Generated SQL</h2>
              </div>
              <div className="aside-section">
                {sqlShown ? (
                  <div className="sql-block">
                    <pre className="code code-dark" style={{ paddingRight: 64 }}>{sqlShown}</pre>
                    <CopyButton text={sqlShown} />
                  </div>
                ) : previewError && table ? (
                  <div className="error-banner" style={{ marginBottom: 0 }}>{previewError}</div>
                ) : (
                  <p className="panel-note" style={{ margin: 0 }}>
                    {table ? "Building..." : "Pick a table to see the query."}
                  </p>
                )}
              </div>
            </section>

            {table && (
              <section className="panel">
                <div className="panel-head">
                  <h2 className="panel-title">Index and run</h2>
                </div>
                <div className="aside-section">
                  <div className="field">
                    <label>
                      Index columns
                      <span className="field-hint-inline"> (pick several for a composite index)</span>
                      {indexColumns.length === 0 && filterColumn && <span className="field-hint-inline"> (defaults to {filterColumn})</span>}
                    </label>
                    <div className="chips">
                      {columns.map((c) => {
                        const position = indexColumns.indexOf(c.columnName);
                        return (
                          <label key={c.columnName} className="chip">
                            <input type="checkbox" checked={position >= 0} onChange={() => toggleIndexColumn(c.columnName)} />
                            {position >= 0 && indexColumns.length > 1 && <span className="chip-pos">{position + 1}</span>}
                            {c.columnName}
                            {c.indexed && <span className="chip-idx" title="Already indexed">idx</span>}
                          </label>
                        );
                      })}
                    </div>
                    {indexColumns.length > 1 && (
                      <p className="field-hint">
                        Column order matters: the planner can use this index for filters on <span className="mono">{indexColumns[0]}</span>,
                        or on it together with the columns after it, but not on the later columns alone.
                      </p>
                    )}
                  </div>
                  {alreadyIndexed && (
                    <p className="inline-warning">
                      <span className="mono">{effectiveIndexColumns[0]}</span> already has an index that PLANSHIFT won't
                      drop, so the baseline will use it too. Expect little or no change.
                    </p>
                  )}
                  <div className="form-row" style={{ marginTop: 12, gap: 12 }}>
                    <div className="field">
                      <label>Repetitions</label>
                      <input className="input input-num" type="number" min={1} max={20} value={repetitions} onChange={(e) => setRepetitions(Number(e.target.value))} />
                    </div>
                    <div className="field">
                      <label>Threshold (%)</label>
                      <input className="input input-num" type="number" min={1} max={100} value={thresholdPercent} onChange={(e) => setThresholdPercent(Number(e.target.value))} />
                    </div>
                  </div>

                  <div className="aside-actions">
                    <button className="btn btn-primary" onClick={runExperiment} disabled={running}>
                      {running ? "Starting..." : "Run experiment"}
                    </button>
                    <button className="btn" onClick={predictImpact} disabled={predicting}>
                      {predicting ? "Estimating..." : "Estimate impact first"}
                    </button>
                  </div>

                  {actionError && <div className="error-banner" style={{ marginTop: 12, marginBottom: 0 }}>{actionError}</div>}

                  {predictedImpact && (
                    <div className="predict">
                      <div className="label">
                        Estimated change{" "}
                        <span style={{ color: "var(--ink-3)", fontWeight: 400 }}>
                          ({predictedImpact.method === "historical" ? "from past runs" : "rule of thumb"})
                        </span>
                      </div>
                      <div className={`predict-value change-value ${changeClassName(predictedImpact.predictedPercentageChange)}`}>
                        <AnimatedNumber value={predictedImpact.predictedPercentageChange} format={formatChangeText} />
                      </div>
                      <p className="predict-note">
                        {predictedImpact.note} Current plan: <span className="mono">{predictedImpact.currentScanType}</span>,
                        selectivity <span className="mono">{(predictedImpact.selectivity * 100).toFixed(1)}%</span>. An
                        estimate, not a measurement.
                      </p>
                    </div>
                  )}
                </div>
              </section>
            )}
          </aside>
        </div>
      )}
    </div>
  );
}
