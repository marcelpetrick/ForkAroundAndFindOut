# Working agreement

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

## Scope and autonomy

- Read `vision.md` and implement its complete applicable product scope. Make routine
  decisions independently and continue until the product works.
- Preserve the vision's explicit conditions for later research: learned models need
  real training sessions; image classifiers, depth, and Raspberry Pi are conditional
  future work, not claims of delivered capabilities.
- Never equate synthetic tests with physical-camera or household validation. Record
  unavailable evidence and remaining acceptance work honestly.

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
