const WIDTH = 100;
const HEIGHT = 40;
const PAD = 4;

/**
 * A small line chart of overall percentage change across the most recent
 * experiments, oldest to newest. Kept deliberately minimal (no axes, no
 * labels) -- it's a shape, not a chart to read precise values off of; the
 * exact numbers are already in the experiments table below it.
 */
export function TrendSparkline({ values }: { values: number[] }) {
  if (values.length < 2) return null;

  const min = Math.min(...values, 0);
  const max = Math.max(...values, 0);
  const span = max - min || 1;
  const stepX = (WIDTH - PAD * 2) / (values.length - 1);

  const points = values.map((v, i) => {
    const x = PAD + i * stepX;
    const y = HEIGHT - PAD - ((v - min) / span) * (HEIGHT - PAD * 2);
    return [x, y] as const;
  });

  const linePath = points.map(([x, y], i) => `${i === 0 ? "M" : "L"}${x.toFixed(1)},${y.toFixed(1)}`).join(" ");
  const zeroY = HEIGHT - PAD - ((0 - min) / span) * (HEIGHT - PAD * 2);
  const [firstX] = points[0];
  const [lastX] = points[points.length - 1];
  const areaPath = `${linePath} L${lastX.toFixed(1)},${zeroY.toFixed(1)} L${firstX.toFixed(1)},${zeroY.toFixed(1)} Z`;

  const last = values[values.length - 1];
  const lineColor = last <= 0 ? "var(--positive)" : last > 0 ? "var(--negative)" : "var(--neutral)";

  return (
    <svg
      width={220}
      height={HEIGHT}
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      preserveAspectRatio="none"
      role="img"
      aria-label={`Trend of overall performance change across the last ${values.length} experiments`}
    >
      <line x1={PAD} y1={zeroY} x2={WIDTH - PAD} y2={zeroY} stroke="var(--border)" strokeWidth={0.5} strokeDasharray="1.5 2" />
      <path d={areaPath} fill={lineColor} opacity={0.12} stroke="none" />
      <path
        d={linePath}
        fill="none"
        stroke={lineColor}
        strokeWidth={1.6}
        strokeLinejoin="round"
        strokeLinecap="round"
        vectorEffect="non-scaling-stroke"
        pathLength={100}
        className="sparkline-path"
      />
      {points.map(([x, y], i) => (
        <circle key={i} cx={x} cy={y} r={i === points.length - 1 ? 2.2 : 1.2} fill={lineColor} opacity={i === points.length - 1 ? 1 : 0.55} />
      ))}
    </svg>
  );
}
