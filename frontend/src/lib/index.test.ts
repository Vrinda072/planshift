import { describe, expect, it } from "vitest";
import { indexLabel } from "./index";

describe("indexLabel", () => {
  it("defaults to the built-in target", () => {
    expect(indexLabel({ targetTable: null, targetColumn: null })).toBe("orders.customer_id");
  });

  it("lists composite columns in order", () => {
    expect(indexLabel({ targetTable: "airbnb", targetColumn: "room_type,price" })).toBe("airbnb (room_type, price)");
  });

  it("marks hash indexes", () => {
    expect(indexLabel({ targetTable: "airbnb", targetColumn: "room_type", indexMethod: "hash" })).toBe("airbnb.room_type · hash");
  });
});
