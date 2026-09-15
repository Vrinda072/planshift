import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { InfoTooltip } from "./InfoTooltip";

describe("InfoTooltip", () => {
  it("renders the tooltip text", () => {
    render(<InfoTooltip text="Median across all repetitions." />);
    expect(screen.getByText("Median across all repetitions.")).toBeInTheDocument();
  });

  it("is focusable so keyboard users can reach it", () => {
    render(<InfoTooltip text="Some help text" />);
    expect(screen.getByText("i")).toHaveAttribute("tabIndex", "0");
  });
});
