# Quality Gates

## How to use this document

Each milestone must pass all listed gates before the next milestone starts. A gate is pass/fail, not advisory.

## Global gates (apply to all milestones)

- CI health:
  - `pnpm typecheck` passes.
  - `pnpm build` passes.
- No open P0 issues.
- Documentation updated for any contract or behavior changes.

## Milestone 0 gates

- Roadmap, contract, and quality docs exist and are linked from `README.md`.
- `core-bridge` type exports are documented and versioned.

## Milestone 1 gates

- Android host launches on emulator and physical Android TV.
- D-pad can reach every interactive control with visible focus indicator.
- Back handling follows policy:
  - modal close
  - route back
  - app exit
- Host lifecycle/network events are visible in web event stream.

## Milestone 2 gates

- Playback starts and runs for at least 30 seconds.
- Pause/resume/stop are reflected in host and web shell.
- Failures emit typed telemetry with stable error code taxonomy.
- Smoke script covers start/pause/resume/stop/failure.

## Milestone 3 gates

- Login persists across restart.
- Library sync SLA target met under normal network (<10s).
- Watch progress sync confirmed after stop and restart.
- Deep link and add-on install flow validated.

## Milestone 4 gates

- Crash reporting visible in monitoring stack.
- Diagnostics export includes session, device, and recent bridge events.
- Fault injection checks pass:
  - offline
  - timeout
  - malformed payload
- No unhandled promise rejection in smoke suite.

## Milestone 5 gates

- Signed internal build distributed to test cohort.
- E2E smoke matrix pass rate >= 95%.
- Crash-free session rate >= 98%.
- All P1/P2 issues have owner + ETA.

## Milestone 6 gates

- Desktop host decision recorded in ADR.
- Weighted matrix includes delivery, runtime, and maintenance criteria.
- Post-beta desktop backlog and sequencing documented.
