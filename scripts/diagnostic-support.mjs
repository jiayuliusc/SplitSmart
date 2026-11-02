// Deliberately fixed loopback URL and demo ID: scripts cannot target a cloud project.
export const projectId = "demo-splitsmart";
export const firestoreOrigin = "http://127.0.0.1:8080";
export const documentsUrl = `${firestoreOrigin}/v1/projects/${projectId}/databases/(default)/documents`;
export const fixtureUrl = `${documentsUrl}/_diagnostics/connectivity`;
export const fixture = {
  fields: {
    message: { stringValue: "SplitSmart emulator connection OK" },
    projectId: { stringValue: projectId },
    schemaVersion: { integerValue: "1" }
  }
};

export async function request(url, options = {}) {
  return fetch(url, { ...options, signal: AbortSignal.timeout(10000) });
}

export async function assertDemoEmulators() {
  const hub = await request("http://127.0.0.1:4400/emulators");
  if (!hub.ok) throw new Error(`Emulator hub unavailable: HTTP ${hub.status}`);
  const running = await hub.json();
  for (const [name, port] of [["auth", 9099], ["firestore", 8080], ["functions", 5001]]) {
    if (running[name]?.port !== port) throw new Error(`${name} emulator must run on port ${port}`);
  }
}
