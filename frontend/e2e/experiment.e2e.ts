import { expect, test } from "@playwright/test";

const ROWS = 20_000;

// Asserts the workflow and the shape of the result. Never timings: those depend on the machine.
test("import a CSV, preview a query, run an experiment and see both plans", async ({ page }) => {
  const table = `e2e_people_${Date.now()}`;
  const csv = ["id,city", ...Array.from({ length: ROWS }, (_, i) => `${i + 1},city_${i % 50}`)].join("\n");

  await page.goto("/query-builder");
  await page.getByLabel("CSV file").setInputFiles({ name: `${table}.csv`, mimeType: "text/csv", buffer: Buffer.from(csv) });
  await page.getByLabel("Table name").fill(table);
  await page.getByRole("button", { name: "Import", exact: true }).click();
  await expect(page.getByText(`Imported ${ROWS.toLocaleString("en-US")} rows`)).toBeVisible();
  await expect(page.getByLabel("Table", { exact: true })).toHaveValue(table);

  await page.getByLabel("Column", { exact: true }).selectOption("id");
  await page.getByLabel("Value").fill("1234");
  await expect(page.locator("pre.code")).toContainText(`FROM "${table}" WHERE "id" = :filterValue`);

  await page.locator(".aside-section .chip", { hasText: "id" }).click();
  await page.getByRole("button", { name: "Run experiment" }).click();

  await expect(page).toHaveURL(/\/experiments\/\d+$/);
  await expect(page.getByText("Completed")).toBeVisible({ timeout: 60_000 });

  await page.getByRole("button", { name: "See runs and plan" }).click();
  const details = page.locator(".accordion.open");
  await expect(details.locator(".plan-col-label")).toHaveText(["Before", "After"]);
  await expect(details.getByText("Seq Scan", { exact: true })).toBeVisible();
  await expect(details.getByText(/via planshift_exp_\d+/)).toBeVisible();
});
