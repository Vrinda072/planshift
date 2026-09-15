import type { ExperimentQueryResult, RegressionStatus } from "../api/types";
import { formatMs } from "../format";

const VIEWBOX_WIDTH = 660;
const LABEL_WIDTH = 190;
const VALUE_WIDTH = 64;
const CHART_RIGHT_PAD = 8;
const BAR_AREA_X = LABEL_WIDTH;
const BAR_AREA_WIDTH = VIEWBOX_WIDTH - LABEL_WIDTH - VALUE_WIDTH - CHART_RIGHT_PAD;

const BAR_HEIGHT = 10;
const BAR_GAP = 3;
const ROW_PAD_Y = 7;
const ROW_HEIGHT = ROW_PAD_Y * 2 + BAR_HEIGHT * 2 + BAR_GAP;
const AXIS_HEIGHT = 22;
const LEGEND_HEIGHT = 26;

const MIN_BAR_WIDTH = 5;

function statusColor(status: RegressionStatus): string {
  if (status === "IMPROVED") return "var(--positive)";
  if (status === "REGRESSED") return "var(--negative)";
  return "var(--neutral)";
}

function truncate(text: string, maxChars: number): string {
  return text.length > maxChars ? text.slice(0, maxChars - 1) + "…" : text;
}

/**
 * A horizontal grouped bar chart (baseline vs. candidate median time) above
 * the per-query results table. Bar length uses a log scale -- with query
 * times spanning ~0.15ms to ~250ms in real runs, a linear scale would
 * render the fast lookups as invisible slivers next to the join query.
 * Log scale keeps every bar meaningfully visible; the axis is labeled
 * accordingly so it doesn't read as linear by accident.
 */
export function QueryComparisonChart({ results }: { results: ExperimentQueryResult[] }) {
  if (results.length === 0) {
    return null;
  }

  const values = results.flatMap((r) => [r.baselineMedianMs, r.candidateMedianMs]).filter((v) => v > 0);
  const minVal = Math.min(...values);
  const maxVal = Math.max(...values);
  const logMin = Math.log10(minVal);
  const logMax = Math.log10(maxVal);
  const logSpan = logMax - logMin;

  const barWidth = (value: number): number => {
    if (value <= 0) return 0;
    if (logSpan === 0) return BAR_AREA_WIDTH;
    const t = (Math.log10(value) - logMin) / logSpan;
    return MIN_BAR_WIDTH + t * (BAR_AREA_WIDTH - MIN_BAR_WIDTH);
  };

  // Axis ticks at each power of ten spanned by the data, so the log scale
  // is legible rather than implied.
  const tickExponents: number[] = [];
  for (let e = Math.floor(logMin); e <= Math.ceil(logMax); e++) {
    tickExponents.push(e);
  }

  const chartHeight = LEGEND_HEIGHT + results.length * ROW_HEIGHT + AXIS_HEIGHT;

  return (
    <div>
      <svg
        viewBox={`0 0 ${VIEWBOX_WIDTH} ${chartHeight}`}
        width="100%"
        height={chartHeight}
        role="img"
        aria-label="Baseline vs. candidate median execution time per query, log scale"
      >
        {/* legend */}
        <g transform={`translate(${LABEL_WIDTH}, 14)`}>
          <rect x={0} y={-7} width={14} height={BAR_HEIGHT} rx={3} fill="var(--border-strong)" />
          <text x={19} y={1} fontSize={10.5} fill="var(--text-secondary)">Before</text>
          <rect x={70} y={-7} width={14} height={BAR_HEIGHT} rx={3} fill="var(--text-tertiary)" />
          <text x={89} y={1} fontSize={10.5} fill="var(--text-secondary)">After</text>
          <text x={150} y={1} fontSize={10.5} fill="var(--text-tertiary)">
            (colored by outcome: improved / unchanged / regressed)
          </text>
        </g>

        {/* rows */}
        {results.map((r, i) => {
          const rowY = LEGEND_HEIGHT + i * ROW_HEIGHT;
          const baselineW = barWidth(r.baselineMedianMs);
          const candidateW = barWidth(r.candidateMedianMs);
          const color = statusColor(r.status);

          return (
            <g key={r.queryId}>
              <title>{`${r.queryName}: ${formatMs(r.baselineMedianMs)} -> ${formatMs(r.candidateMedianMs)} (${r.status.toLowerCase()})`}</title>
              <text
                x={LABEL_WIDTH - 10}
                y={rowY + ROW_PAD_Y + BAR_HEIGHT + BAR_GAP / 2 + 3}
                fontSize={11.5}
                textAnchor="end"
                fill="var(--text-primary)"
              >
                {truncate(r.queryName, 26)}
              </text>

              {/* baseline bar */}
              <rect
                x={BAR_AREA_X}
                y={rowY + ROW_PAD_Y}
                width={Math.max(baselineW, MIN_BAR_WIDTH)}
                height={BAR_HEIGHT}
                rx={3}
                fill="var(--border-strong)"
              />
              <text
                x={BAR_AREA_X + Math.max(baselineW, MIN_BAR_WIDTH) + 6}
                y={rowY + ROW_PAD_Y + BAR_HEIGHT - 1}
                fontSize={10}
                fontFamily="var(--font-mono)"
                fill="var(--text-tertiary)"
              >
                {formatMs(r.baselineMedianMs)}
              </text>

              {/* candidate bar */}
              <rect
                x={BAR_AREA_X}
                y={rowY + ROW_PAD_Y + BAR_HEIGHT + BAR_GAP}
                width={Math.max(candidateW, MIN_BAR_WIDTH)}
                height={BAR_HEIGHT}
                rx={3}
                fill={color}
              />
              <text
                x={BAR_AREA_X + Math.max(candidateW, MIN_BAR_WIDTH) + 6}
                y={rowY + ROW_PAD_Y + BAR_HEIGHT + BAR_GAP + BAR_HEIGHT - 1}
                fontSize={10}
                fontFamily="var(--font-mono)"
                fill="var(--text-secondary)"
              >
                {formatMs(r.candidateMedianMs)}
              </text>
            </g>
          );
        })}

        {/* log-scale axis */}
        {tickExponents.map((e) => {
          const value = 10 ** e;
          const x = BAR_AREA_X + barWidth(value);
          const axisY = LEGEND_HEIGHT + results.length * ROW_HEIGHT;
          if (x < BAR_AREA_X || x > BAR_AREA_X + BAR_AREA_WIDTH) return null;
          return (
            <g key={e}>
              <line x1={x} y1={LEGEND_HEIGHT} x2={x} y2={axisY} stroke="var(--border)" strokeWidth={1} />
              <text x={x} y={axisY + 14} fontSize={9.5} textAnchor="middle" fill="var(--text-tertiary)">
                {value < 1 ? value.toFixed(1) : value.toLocaleString()}ms
              </text>
            </g>
          );
        })}
        <text
          x={VIEWBOX_WIDTH}
          y={LEGEND_HEIGHT + results.length * ROW_HEIGHT + 14}
          fontSize={9.5}
          textAnchor="end"
          fill="var(--text-tertiary)"
        >
          log scale
        </text>
      </svg>
    </div>
  );
}
