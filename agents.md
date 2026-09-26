# Working agreement

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

## Scope and autonomy

- Read `vision.md` and implement its complete applicable product scope. Make routine
  decisions independently and continue until the product works.
- Preserve the vision's explicit conditions for later research: learned models need
  real training sessions; image classifiers and depth are conditional future work, not
  claims of delivered capabilities.
- Never build the Raspberry Pi / multi-camera appliance (owner decision 2026-09-26).
- Never equate synthetic tests with physical-camera or household validation. Record
  unavailable evidence and remaining acceptance work honestly.

## Collaboration and working order

- Other agents may work in the same checkout. Stage and commit only files you changed,
  by explicit path; never `git add -A` / `git add .`, and never revert others' files.
- `plan_v2/` is written by a planning agent. Do not edit it; read it at the end of the
  current task batch and fold its decisions into the remaining work.
- Update `plan.md` first when the plan changes, commit and push it, then continue.
  Record the owner's instructions in its owner input log.
- Work step by step until the whole project is done; do not stop early.
- Only when no implementation work remains: review your own changes, fix the errors
  found, then check `plan_v2/` again.
- `localPipeline.sh` and the README badge set follow `~/repos/myLastFmPlayer` and
  `~/repos/Cullendula` (usage text, numbered stages, stage logs, final summary).
- When everything is complete and verified, publish a public GitHub release.

## Product stack

- Use Kotlin throughout the Android product, including its native UI. The user
  explicitly selected Kotlin and rejected a mixed Flutter/Kotlin implementation.
- Use CameraX and MediaPipe Pose Landmarker behind a replaceable pose adapter.
- Docker distributes the APK; it does not run the phone camera on a server.

## Git and releases

- Work directly on `main`. Preserve user changes.
- Use atomic commits and conventional commit messages. Push verified commits
  continuously; never commit or push a known failing change.
- Use semantic versioning. Every commit increments the patch version; a major feature
  also increments the minor version. `VERSION` is the release source of truth.
- Update `plan.md` in each commit with the step, completed work, pending work,
  validation evidence, and new ideas. Identify entries by version and commit subject
  because a commit cannot contain its own hash.

## Quality and delivery

- Establish `localPipeline.sh` as early as possible. It must grow to include formatting,
  linting, type/static checks, unit/integration tests, coverage, builds, end-to-end tests,
  and Docker validation. GitHub Actions must run the same gates.
- Maintain at least 95% test coverage. State measured scope and do not exclude product
  logic merely to meet the threshold.
- Make the local product work before Dockerizing it. Build and publish the resulting
  Docker image to GHCR through GitHub Actions.
- Write small reusable scripts for repeated tasks and document every script.
- Use GPLv3-or-later, provide the full `LICENSE`, and add appropriate copyright and
  SPDX headers to authored source files.
- Maintain a README with badges inspired by `~/repos/Cullendula` or
  `~/repos/myLastFmPlayer`, setup, usage, testing, pipeline and Docker instructions,
  and at least one genuine screenshot of the running UI.

## Completion gate

- Run `/reviewBranch`, fix confirmed issues, then run `/githubAbout`.
- Audit every section and requirement in `vision.md` against implementation and tests.
- Run the full local pipeline; verify GitHub Actions, the published Docker image,
  pushed commits, and a clean working tree.
- Do not declare the entire vision complete while required work or evidence remains.
