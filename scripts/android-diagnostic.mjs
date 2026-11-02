import { spawnSync } from "node:child_process";

// cmd.exe is needed for the checked-in .bat wrapper on native Windows.
const windows = process.platform === "win32";
const result = spawnSync(windows ? "cmd.exe" : "./gradlew",
  windows ? ["/d", "/c", "gradlew.bat connectedLocalDebugAndroidTest"]
    : ["connectedLocalDebugAndroidTest"],
  { stdio: "inherit" });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
