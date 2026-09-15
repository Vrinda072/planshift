import { useEffect, useRef, useState } from "react";

function prefersReducedMotion(): boolean {
  return typeof window !== "undefined" && window.matchMedia?.("(prefers-reduced-motion: reduce)").matches === true;
}

/**
 * Animates a numeric value from its previously rendered value to a new one
 * whenever `value` changes -- a stat that updates (e.g. after a fresh
 * experiment completes) counts up/down instead of just replacing the text,
 * which is a much clearer signal that something changed. Runs off
 * requestAnimationFrame rather than a CSS transition because the thing
 * being animated is the number itself, not a style property.
 *
 * With reduced motion requested, the hook's internal state is bypassed
 * entirely (the function returns `value` directly below) rather than
 * synced via a setState call inside the effect -- that would just be an
 * unnecessary extra render to arrive at a value the caller already has.
 */
export function useAnimatedNumber(value: number, durationMs = 650): number {
  const [display, setDisplay] = useState(value);
  const fromRef = useRef(value);
  const rafRef = useRef<number | undefined>(undefined);
  const reduced = prefersReducedMotion();

  useEffect(() => {
    if (reduced) {
      fromRef.current = value;
      return;
    }

    const from = fromRef.current;
    const to = value;
    if (from === to) return;

    const start = performance.now();

    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      setDisplay(from + (to - from) * eased);
      if (t < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = to;
      }
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => {
      if (rafRef.current !== undefined) cancelAnimationFrame(rafRef.current);
    };
  }, [value, durationMs, reduced]);

  return reduced ? value : display;
}
