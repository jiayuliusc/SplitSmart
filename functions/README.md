# Future authoritative operations

This is a compilable TypeScript Functions project with zero handlers. `npm ci` at
the repository root installs both workspaces; `npm run functions:build` compiles it.
`npm run emulators` starts the Functions emulator along with Auth and Firestore.
An empty discovered function list is expected.

Future modules belong in `src/identity`, `src/groups`, `src/expenses`, and
`src/settlements`. Add a module only when implementing that workstream. Coordinate
exports in `src/index.ts`, callable names, regions, payloads, and errors first.
See [contracts](../docs/CONTRACTS.md) and [schema](../docs/SCHEMA.md).

Every future operation must authenticate and authorize independently, validate
authoritative data, and handle retries. Admin SDK access bypasses Firestore rules;
deny-by-default rules alone cannot secure a callable. No registration, group,
expense, balance, or settlement behavior is implemented here. Do not deploy this
foundation or enable billing as part of local setup.
