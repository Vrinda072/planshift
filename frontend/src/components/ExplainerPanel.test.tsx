import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ExplainerPanel } from "./ExplainerPanel";

describe("ExplainerPanel", () => {
  it("renders every pipeline step", () => {
    render(<ExplainerPanel />);
    expect(screen.getByText("generate or import data")).toBeInTheDocument();
    expect(screen.getByText("pick a query")).toBeInTheDocument();
    expect(screen.getByText("compare")).toBeInTheDocument();
  });

  it("renders one fewer arrow than there are steps", () => {
    const { container } = render(<ExplainerPanel />);
    const steps = container.querySelectorAll(".pipeline-step");
    const arrows = container.querySelectorAll(".pipeline-arrow");
    expect(arrows.length).toBe(steps.length - 1);
  });
});
