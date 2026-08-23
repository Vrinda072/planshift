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
      const base = f.name.replace(/\.csv$/i, "");
      setTableName(base);
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
    <div className="card" style={{ marginBottom: 24 }}>
      <h2>Import your own dataset</h2>
      <p style={{ color: "var(--text-secondary)", marginTop: 0, fontSize: 13 }}>
        Upload a CSV (e.g. from Kaggle). Postgres itself parses and loads it via <code>COPY</code>; column
        types are inferred from the data. The new table appears in the picker below immediately.
      </p>
      <div className="form-row" style={{ flexWrap: "wrap" }}>
        <div className="form-field">
          <label>CSV file</label>
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={(e) => handleFileChange(e.target.files?.[0] ?? null)}
            style={{ fontSize: 12.5 }}
          />
        </div>
        <div className="form-field">
          <label>Table name</label>
          <input
            type="text"
            value={tableName}
            onChange={(e) => setTableName(e.target.value)}
            style={{ width: 160 }}
          />
        </div>
        <button className="btn btn-primary" onClick={doImport} disabled={importing || !file}>
          {importing ? "Importing..." : "Import"}
        </button>
      </div>
      {success && <div style={{ color: "var(--positive)", fontSize: 13, marginTop: 8 }}>{success}</div>}
      {error && <div className="error-banner" style={{ marginTop: 12 }}>{error}</div>}
    </div>
  );
}
