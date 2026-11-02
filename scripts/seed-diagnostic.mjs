import { assertDemoEmulators, fixture, fixtureUrl, request } from "./diagnostic-support.mjs";

await assertDemoEmulators();
const response = await request(fixtureUrl, {
  method: "PATCH",
  // Emulator-only administrator bypass. No account or cloud credential is involved.
  headers: { Authorization: "Bearer owner", "Content-Type": "application/json" },
  body: JSON.stringify(fixture)
});
if (!response.ok) throw new Error(`Fixture write failed: HTTP ${response.status}: ${await response.text()}`);
console.log("Seeded demo-splitsmart/_diagnostics/connectivity (synthetic data only).");
