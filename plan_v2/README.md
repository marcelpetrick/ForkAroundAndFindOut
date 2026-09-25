# plan_v2 workspace

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

This subdirectory holds the **second-generation plan** for Fork Around & Find Out and
the UI/UX design work that goes with it. It was produced by Claude (Fable 5.1) on
2026-09-25 as a full review of the repository at commit `c32d9a0` plus the
uncommitted work visible at that time, at the owner's request; §2.3 of the plan
re-checks every finding against `49e660c`, which landed while the review was being
written. Nothing in here is product code; the app never reads this directory. The
implementing agent treats this directory as read-only (see `agents.md`).

| File | What it is |
| --- | --- |
| [`plan_v2.md`](plan_v2.md) | The review of every aspect of the project, the architecture and technology decisions with reasoning and alternatives, the ranked findings, the re-sequenced delivery plan, the vision traceability matrix, the hardware validation protocol, and the self-review log. |
| [`design/ui-ux.md`](design/ui-ux.md) | Design system (colour, type, spacing, components, states, copy), navigation flow, and a per-screen specification. |
| [`design/*.svg`](design/) | Screen mockups referenced from `ui-ux.md`. Plain SVG so GitHub renders them; no binary assets. |

How to use it: `plan.md` at the repository root stays the running ledger that every
commit updates. `plan_v2.md` is the plan *of record* for the remaining work; when a
milestone from it is done, tick it in `plan_v2.md` and add the ledger entry in `plan.md`
as before. If the two disagree, `plan_v2.md` wins for what to build and `plan.md` wins
for what was built.
