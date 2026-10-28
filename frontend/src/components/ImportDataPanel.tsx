import { useState } from "react";
import { api } from "../api/client";

export function ImportDataPanel({ onImported }: { onImported: (tableName: string) => void }) {
  const [file, setFile] = useState<File | null>(null);
  const [tableName, setTableName] = useState("");
  const [importing, setImporting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const handleFileChange = (f: File | null) => {
    setFile(f);
    setSuccess(null);
    setError(null);
    if (f && !tableName) {
      setTableName(f.name.replace(/\.csv$/i, ""));
    }
  };

  const doImport = async () => {
    if (!file || !tableName) {
      setError("Choose a CSV file and a table name first.");
      return;
    }
    setImporting(true);
    setError(null);
    setSuccess(null);
    try {
      const result = await api.importCsv(file, tableName);
      setSuccess(`Imported ${result.rowCount.toLocaleString()} rows into "${result.tableName}" (${result.columnNames.length} columns).`);
      onImported(result.tableName);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setImporting(false);
    }
  };

  return (
    <div>
      <p className="panel-note" style={{ marginBottom: 12 }}>
        Or upload a CSV (up to 50 MB). Postgres loads it with <code>COPY</code> and infers a type per column.
      </p>
      <div className="form-row">
        <div className="form-field">
          <label>CSV file</label>
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
            style={{ width: 230, paddingTop: 5, fontSize: 12 }}
          />
        </div>
        <div className="form-field">
          <label>Table name</label>
          <input type="text" value={tableName} onChange={(e) => setTableName(e.target.value)} style={{ width: 160 }} />
        </div>
        <button className="btn" onClick={doImport} disabled={importing || !file}>
          {importing ? "Importing..." : "Import"}
        </button>
      </div>
      {success && (
        <div className="fade-section" style={{ marginTop: 10, color: "var(--good)", fontSize: 13 }}>
          {success}
        </div>
      )}
      {error && <div className="error-banner" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
    </div>
  );
}
