/**
 * Animated placeholder rows shaped like a real data table, shown while a
 * page's first fetch is in flight. Renders actual <table>/<tr>/<td>
 * markup (reusing the app's real table CSS) so row height and spacing
 * match the content that replaces it -- no layout jump when data arrives.
 */
export function Skeleton({ rows = 4, columns = 4 }: { rows?: number; columns?: number }) {
  return (
    <div className="table-scroll">
      <table>
        <tbody>
          {Array.from({ length: rows }).map((_, r) => (
            <tr key={r}>
              {Array.from({ length: columns }).map((_, c) => (
                <td key={c}>
                  <div
                    className="skeleton-bar"
                    style={{ width: `${barWidthPercent(r, c, columns)}%`, animationDelay: `${(r * columns + c) * 40}ms` }}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Deterministic width variation so bars read as text placeholders, not a uniform grid. */
function barWidthPercent(row: number, col: number, totalColumns: number): number {
  const isFirstColumn = col === 0;
  const base = isFirstColumn ? 70 : 45;
  const wobble = ((row * 7 + col * 13) % 5) * 4;
  return Math.min(base + wobble, totalColumns === 1 ? 100 : 90);
}
