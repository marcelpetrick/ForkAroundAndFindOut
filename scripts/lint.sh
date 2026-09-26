#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Run every non-Gradle linter through pinned container images (reproducible locally and in CI):
#   reuse (REUSE 3.x / SPDX), ruff check + format (Python), yamllint (YAML), xmllint (XML/SVG),
#   hadolint (Dockerfiles), actionlint (GitHub workflows), markdownlint-cli2 (Markdown).
# Kotlin is linted by Gradle (ktlint via Spotless, detekt, Android lint); shell by ShellCheck.
# Usage: scripts/lint.sh [TOOL...]   (default: all; e.g. scripts/lint.sh reuse ruff)
set -euo pipefail
cd "$(dirname "$0")/.."

LINT_IMAGE="fork-lint:local" # built from docker/lint/Dockerfile (pinned base and packages)
HADOLINT="hadolint/hadolint:v2.15.1@sha256:32dac94127fd60b7b7e3fbfc65e1383b9b5e25c9bfd7b8536de7a539fe68a12d"
ACTIONLINT="rhysd/actionlint:1.7.12@sha256:b1934ee5f1c509618f2508e6eb47ee0d3520686341fec936f3b79331f9315667"
MARKDOWNLINT="davidanson/markdownlint-cli2:v0.23.3@sha256:d5f3f3f04b2e285dcbcdcd13b4454d119e273e3c393a9dabd163dba4abad526d"

toolbox() { docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp -v "${PWD}:/work" -w /work "${LINT_IMAGE}" "$@"; }

# Quiet when compliant; the full report (which files, which tags) when not.
lint_reuse() { toolbox reuse lint --quiet || { toolbox reuse lint; return 1; }; }
lint_ruff() { toolbox ruff check scripts && toolbox ruff format --check scripts; }
lint_yaml() { toolbox yamllint --strict .github .yamllint.yaml .markdownlint-cli2.yaml; }
lint_xml() {
    local files
    mapfile -t files < <(git ls-files '*.xml' '*.svg')
    toolbox xmllint --noout "${files[@]}"
}
lint_docker() {
    local file
    for file in Dockerfile docker/lint/Dockerfile; do
        docker run --rm -i "${HADOLINT}" hadolint --failure-threshold info - < "${file}" || return 1
    done
}
lint_actions() { docker run --rm -v "${PWD}:/repo" -w /repo "${ACTIONLINT}" -color; }
lint_markdown() { docker run --rm -u "$(id -u):$(id -g)" -v "${PWD}:/workdir" "${MARKDOWNLINT}"; }

all=(reuse ruff yaml xml docker actions markdown)
selected=("$@")
[[ ${#selected[@]} -gt 0 ]] || selected=("${all[@]}")
docker build -q -t "${LINT_IMAGE}" docker/lint >/dev/null
failed=()
for tool in "${selected[@]}"; do
    printf '=== lint: %s ===\n' "${tool}"
    if "lint_${tool}"; then printf '    %s: clean\n' "${tool}"; else failed+=("${tool}"); fi
done
if [[ ${#failed[@]} -gt 0 ]]; then
    printf 'Linters with findings: %s\n' "${failed[*]}" >&2
    exit 1
fi
printf 'All %d linters clean: %s\n' "${#selected[@]}" "${selected[*]}"
