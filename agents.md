<!-- SPDX-FileCopyrightText: 2026 Marcel Petrick -->
<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# Working agreement

The owner's rules for every agent working on this repository. They are collected from the
owner's instructions; the dated originals are in the owner input log in `plan.md`. When a new
instruction arrives, add it here in the fitting section and record it in `plan.md`.

## Scope and autonomy

- Read `vision.md` and implement its complete applicable product scope. Make routine
  decisions independently, as an expert designer, developer and product manager, and
  continue until the product works.
- Preserve the vision's explicit conditions for later research: learned models need
  real training sessions; image classifiers and depth are conditional future work, not
  claims of delivered capabilities.
- Never build the Raspberry Pi / multi-camera appliance (owner decision 2026-09-26).
- Never equate synthetic tests with physical-camera or household validation. Record
  unavailable evidence and remaining acceptance work honestly.
- Short stacked instructions ("get all done", "go go go") mean: finish the whole batch,
  including the release, without stopping to ask.

## Plan first, then iterate

- Every request, however small, goes into `plan.md` before the code: record the owner's
  words in the owner input log, turn them into checklist items with the decisions, commit and
  push the plan, then work the items one commit at a time.
- Review findings (`/reviewBranch`, CI failures, screenshots that reveal a defect) also go
  into the plan as items, and are then fixed; never only reported.
- "What is left?" is answered from `plan.md`: open items, the new-ideas backlog and the
  vision traceability table. Close backlog items with references when earlier work already
  delivered them.
- Work step by step until the whole project is done; do not stop early.

## Collaboration and working order

- Other agents may work in the same checkout. Stage and commit only files you changed,
  by explicit path; never `git add -A` / `git add .`, and never revert others' files.
- `plan_v2/` is written by a planning agent. Do not edit it (its licence tags are asserted in
  `REUSE.toml`); read it at the end of the current task batch and fold its decisions into
  the remaining work.
- Only when no implementation work remains: review your own changes, fix the errors
  found, then check `plan_v2/` again.
- `localPipeline.sh` and the README badge set follow `~/repos/myLastFmPlayer` and
  `~/repos/Cullendula` (usage text, numbered stages, stage logs, final summary).

## Product principles

- Conservative by design: missing or uncertain evidence never *starts* a reminder; hidden
  elbows are "Not visible", never "good posture".
- Design for the real scene, not the empty one: a set table (pots, plates, bottles, glasses)
  hides arms. Handle brief occlusion, bridge only what was clearly seen, tell the family which
  arm is hidden, and run setup checks at the set table.
- Controls never move under a finger: Pause, Stop and other primary controls stay at fixed
  positions; text, hints and banners that change size go below them.
- Setup steps the family must get right (the ten-second visibility check) can be restarted
  from the same screen.
- Plain, friendly copy in English and German; seats are colours, never identities.

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
- Release procedure: full local pipeline green → push → wait for green `main` CI → tag
  `v<VERSION>` → wait for the tag run → verify the release page, the APK download and
  checksum, the signing certificate and the GHCR image. If a tag run fails before publishing,
  delete that unpublished tag, fix, bump, and release the next version. Hand the owner the
  direct APK asset URL.

## Quality gates (all in `localPipeline.sh`, all mirrored by GitHub Actions)

- Linters of every kind for the stack, each pinned, each failing the pipeline: ShellCheck,
  ktlint (Spotless), **detekt** (`detekt.yml`, every deviation explained), Android lint with
  warnings as errors, and the *Lint Suite* (`scripts/lint.sh` in pinned containers): reuse,
  ruff, yamllint, xmllint, hadolint, actionlint and markdownlint.
- Maintain at least 95 % line coverage over all modules (Kover `koverVerifyAll` gate). Every
  new code path gets a test; state the measured scope and never exclude product logic merely
  to meet the threshold.
- Unit, Robolectric and emulator end-to-end tests; e2e tests locate controls by text or
  content description and let scrolling settle before tapping.
- Make the local product work before Dockerizing it. Build and publish the resulting
  Docker image to GHCR through GitHub Actions.
- Write small reusable scripts for repeated tasks and document every script in
  `docs/scripts.md`.
- Run `/updateDependencies` when asked or before a release batch; keep every version pinned.

## Licensing and compliance

- GPL-3.0-or-later with the full `LICENSE`. Obey the GPLv3: appropriate legal notices in the
  app (author, copyright, no warranty, the licence text), corresponding source for every
  binary (public tag linked from the release, the app and the Docker page), and the notices
  every bundled licence asks for.
- REUSE 3.3: every file has `SPDX-FileCopyrightText` and `SPDX-License-Identifier` (or an
  entry in `REUSE.toml`); licence texts live in `LICENSES/`; `reuse lint` gates CI; the
  README carries the REUSE badge.
- A CycloneDX SBOM of exactly what ships is built by the pipeline and attached to every
  release. The About screen lists every bundled component with its licence from the same
  SBOM (`scripts/sbom.py notices --check` keeps them identical).
- Only GPLv3-compatible dependencies; record licence choices for dual-licensed components
  and any open question in `docs/licensing.md`.

## Documentation

- README with badges inspired by `~/repos/Cullendula` or `~/repos/myLastFmPlayer`, setup,
  usage, testing, pipeline and Docker instructions, and genuine screenshots of the running
  UI (light, dark, landscape), refreshed when the UI changes.
- Understandable documents for people, not only for developers: the C4 architecture with
  workflows and the everyday family workflow (`docs/c4-architecture.md`), running the app in
  an emulator on a laptop (`docs/emulator.md`), licensing (`docs/licensing.md`).

## Completion gate

- Run `/reviewBranch`, put the findings into the plan and fix them, then run
  `/githubAbout` and update the About text and topics so they reflect the current state.
- Audit every section and requirement in `vision.md` against implementation and tests.
- Run the full local pipeline; verify GitHub Actions, the published Docker image,
  pushed commits, and a clean working tree.
- Publish a public GitHub release and report its APK URL.
- Do not declare the entire vision complete while required work or evidence remains.
