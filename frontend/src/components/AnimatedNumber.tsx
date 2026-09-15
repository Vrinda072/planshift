import { useAnimatedNumber } from "../hooks/useAnimatedNumber";

export function AnimatedNumber({
  value,
  format = (n: number) => n.toFixed(1),
  durationMs = 650,
}: {
  value: number;
  format?: (n: number) => string;
  durationMs?: number;
}) {
  const display = useAnimatedNumber(value, durationMs);
  return <>{format(display)}</>;
}
