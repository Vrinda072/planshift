import { describe, expect, it } from "vitest";
import { changeClassName, formatChangeText, formatDate, formatMs } from "./format";

describe("formatChangeText", () => {
  it("formats a positive change with an up arrow", () => {
    expect(formatChangeText(12.34)).toBe("↑ 12.3%");
  });

  it("formats a negative change with a down arrow and no leading minus", () => {
    expect(formatChangeText(-8.6)).toBe("↓ 8.6%");
  });

  it("formats zero with a flat arrow", () => {
    expect(formatChangeText(0)).toBe("→ 0.0%");
  });

  it("treats a null-ish value as zero rather than throwing", () => {
    expect(formatChangeText(null as unknown as number)).toBe("→ 0.0%");
  });
});

describe("changeClassName", () => {
  it("maps positive to change-positive", () => {
    expect(changeClassName(5)).toBe("change-positive");
  });

  it("maps negative to change-negative", () => {
    expect(changeClassName(-5)).toBe("change-negative");
  });

  it("maps zero to change-neutral", () => {
    expect(changeClassName(0)).toBe("change-neutral");
  });

  it("maps a null-ish value to change-neutral rather than throwing", () => {
    expect(changeClassName(null as unknown as number)).toBe("change-neutral");
  });
});

describe("formatMs", () => {
  it("uses three decimal places under 10ms", () => {
    expect(formatMs(1.23456)).toBe("1.235ms");
  });

  it("uses one decimal place at or above 10ms", () => {
    expect(formatMs(123.456)).toBe("123.5ms");
  });

  it("formats zero with the sub-10ms precision", () => {
    expect(formatMs(0)).toBe("0.000ms");
  });
});

describe("formatDate", () => {
  it("renders a short month/day/time string", () => {
    const result = formatDate("2026-03-05T14:30:00Z");
    expect(result).toMatch(/\w{3} \d{1,2}/);
  });
});
