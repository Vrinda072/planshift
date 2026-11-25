import { describe, expect, it } from "vitest";
import { createIndexSql } from "./sql";

describe("createIndexSql", () => {
  it("keeps the column order and quotes every identifier", () => {
    expect(
      createIndexSql({ experimentType: "CUSTOM_QUERY", targetTable: "orders", targetColumn: "customer_id,order_date" })
    ).toBe('CREATE INDEX CONCURRENTLY ON "orders" ("customer_id", "order_date");');
  });

  it("offers nothing for a run that changed nothing", () => {
    expect(createIndexSql({ experimentType: "AA_CONTROL", targetTable: "orders", targetColumn: null })).toBeNull();
  });
});
