# Blockers

## Bash unusable (sandbox) — M13 cycle, 2026-10-23

- Symptom: every Bash call fails, even `echo ok`, with
  `apply-seccomp: write /proc/self/setgroups (nested userns is capability-restricted; caller must provide CAP_SYS_ADMIN): Permission denied`.
- Tried: more than 8 repeated calls during the session; `dangerouslyDisableSandbox` is disabled, so no workaround is possible.
- Consequence: impossible to compile, run `bash .athanor/check.sh` or commit. M13 was not started (no file modified, nothing ticked).
- Origin: environment (nested user namespaces forbidden), not the project. The user must allow nested userns (CAP_SYS_ADMIN) or relaunch the pilot outside this isolation.
- Resume: redo M13 from the start (`relations` field in VillageState, Builder, Diplomat, selftest cases), then full check, commit, tick.
