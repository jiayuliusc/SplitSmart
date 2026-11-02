import assert from "node:assert/strict";
import { assertDemoEmulators, documentsUrl, fixture, fixtureUrl, request } from "./diagnostic-support.mjs";

await assertDemoEmulators();
const response = await request(fixtureUrl);
assert.equal(response.status, 200, "Unauthenticated exact diagnostic get must succeed after seeding");
assert.deepEqual((await response.json()).fields, fixture.fields);

// Infrastructure permission checks: no product records or accounts are created.
for (const [name, url, options] of [
  ["diagnostic listing", `${documentsUrl}/_diagnostics`, {}],
  ["other diagnostic path", `${documentsUrl}/_diagnostics/other`, {}],
  ["product document read", `${documentsUrl}/groups/infrastructure-denial-probe`, {}],
  ["client diagnostic write", fixtureUrl, {
    method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(fixture)
  }]
]) {
  const denied = await request(url, options);
  assert.equal(denied.status, 403, `${name} must be denied`);
}

const auth = await request("http://127.0.0.1:9099/emulator/v1/projects/demo-splitsmart/config");
assert.equal(auth.status, 200, "Auth emulator must answer without cloud credentials");
console.log("PASS: Auth/Firestore/Functions registered; diagnostic server read succeeded; list, unrelated reads and client write denied.");
console.log("This host check is separate from the Android SDK instrumentation check (npm run check:android).");
