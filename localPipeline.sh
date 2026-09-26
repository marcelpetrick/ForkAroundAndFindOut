#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Marcel Petrick
# SPDX-License-Identifier: GPL-3.0-or-later
# Local quality pipeline; GitHub Actions runs this same script.
# Stage functions are invoked indirectly through stage():
# shellcheck disable=SC2317,SC2329
set -u
set -o pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="${ANDROID_HOME}/platform-tools/adb"
GRADLE=("${ROOT_DIR}/gradlew" --console=plain)
APP_ID="it.marcelpetrick.fork"
RUN_APP=true
OPEN_REPORTS=true
E2E_MODE="auto"
DOCKER_MODE="auto"
REPORT_DIR=""
LOG_DIR=""
REMOVE_LOG_DIR=true
DETAILS=""

declare -a SUMMARY_LINES=()
declare -A STATUS=()
declare -a MANDATORY=()

print_usage() {
    cat <<EOF
Usage: ./localPipeline.sh [--noRun] [--noOpen] [--e2e auto|required|skip]
                          [--docker auto|required|skip] [--report-dir PATH] [--help]

Local project pipeline (GitHub Actions runs the same script):
  1. Models        download/verify pinned MediaPipe models (SHA-256)
  2. ShellCheck    lint every shell script
  3. Python        byte-compile the helper scripts; verify the generated chime;
                   check relative Markdown links and anchors
  4. Whitespace    reject whitespace errors in the working tree
  5. Lint Suite    scripts/lint.sh in pinned containers: reuse (REUSE/SPDX), ruff, yamllint,
                   xmllint, hadolint, actionlint, markdownlint (needs Docker; follows --docker)
  6. Format        ktlint via Spotless for Kotlin and Gradle Kotlin DSL
  7. Detekt        Kotlin static analysis over all modules (detekt.yml, findings fail)
  8. Android Lint  lint with warnings as errors (Kotlin compiler: -Werror)
  9. Unit Tests    JVM (:detection, :core, :tools) + Robolectric (:app) tests, merged Kover
                   coverage over all modules (>=95% lines, gate fails below), HTML report
 10. APK Build     debug APK and unsigned release APK
 11. SBOM          CycloneDX SBOM of the release runtime classpath plus the bundled models
                   (build/sbom/); the About screen's component list must match it
 12. E2E           instrumented tests on an attached emulator/device
                   auto: run when a device is attached, else WARN; required: FAIL
                   without a device; skip: do not run
 13. Docker        build the APK distribution image (APK, SBOM, licences) and smoke-test it
 14. Open Coverage open the coverage HTML report (suppressed by --noOpen or in CI)
 15. Launch App    install and start the debug APK on an attached device
                   (suppressed by --noRun; never affects the result)
 16. Summary       stage-by-stage PASS/FAIL/WARN/SKIP with details

Every stage runs; a stage whose prerequisite failed is skipped. The exit code is
non-zero when any mandatory stage fails. Use --report-dir to keep stage logs and the
summary; otherwise they are written to a temporary directory and removed.
EOF
}

log() { printf '[INFO] %s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
error() { printf '[ERROR] %s\n' "$*" >&2; }

mark() {
    STATUS["$1"]="$2"
    SUMMARY_LINES+=("$(printf '%-14s : %-4s %s' "$1" "$2" "$3")")
}

ok() {
    local name names
    IFS=',' read -r -a names <<< "$1"
    for name in "${names[@]}"; do
        [[ "${STATUS[${name}]:-}" == "PASS" ]] || return 1
    done
}

# stage NAME MANDATORY(yes/no) PREREQUISITES(comma separated) FUNCTION
stage() {
    local name="$1" mandatory="$2" prerequisites="$3" function="$4"
    local slug="${name// /-}"
    [[ "${mandatory}" == "yes" ]] && MANDATORY+=("${name}")
    if [[ -n "${prerequisites}" ]] && ! ok "${prerequisites}"; then
        mark "${name}" "SKIP" "prerequisite failed: ${prerequisites}"
        return
    fi
    log "=== ${name} ==="
    DETAILS=""
    "${function}" 2>&1 | tee "${LOG_DIR}/${slug,,}.log"
    local result="${PIPESTATUS[0]}"
    [[ -f "${LOG_DIR}/details" ]] && DETAILS="$(cat "${LOG_DIR}/details")" && rm -f "${LOG_DIR}/details"
    case "${result}" in
        0) mark "${name}" "PASS" "${DETAILS}" ;;
        3) mark "${name}" "WARN" "${DETAILS}" ;;
        4) mark "${name}" "SKIP" "${DETAILS}" ;;
        *) mark "${name}" "FAIL" "${DETAILS:-see ${slug,,}.log}" ;;
    esac
}

# Stage functions run in a pipeline subshell; they report details through a file.
detail() { printf '%s' "$*" > "${LOG_DIR}/details"; }

device_attached() {
    [[ -x "${ADB}" ]] && "${ADB}" devices 2>/dev/null | awk 'NR > 1 && $2 == "device"' | grep -q .
}

stage_models() {
    python3 scripts/models.py && detail "Full + Lite verified against models/checksums.json"
}

stage_shellcheck() {
    local scripts
    mapfile -t scripts < <(git ls-files '*.sh'; ls scripts/*.sh 2>/dev/null)
    shellcheck "${scripts[@]}" && detail "$(printf '%s\n' "${scripts[@]}" | sort -u | wc -l) script(s) clean"
}

stage_python() {
    python3 -m compileall -q scripts && python3 scripts/chime.py --check && python3 scripts/check_links.py &&
        detail "$(find scripts -maxdepth 1 -name '*.py' | wc -l) helper script(s) compile; chime matches; Markdown links resolve"
}

stage_whitespace() {
    git diff --check && git diff --cached --check && detail "no whitespace errors"
}

# Docker-based stages share one availability rule: skip on request, WARN (auto) or FAIL (required)
# without a daemon.
docker_ready() {
    if [[ "${DOCKER_MODE}" == "skip" ]]; then
        detail "skipped by --docker skip"
        return 4
    fi
    if ! docker info >/dev/null 2>&1; then
        detail "Docker daemon unavailable"
        [[ "${DOCKER_MODE}" == "required" ]] && return 1
        return 3
    fi
    return 0
}

stage_lint_suite() {
    docker_ready || return $?
    scripts/lint.sh | tee "${LOG_DIR}/lint-suite.out" || return 1
    detail "$(tail -n 1 "${LOG_DIR}/lint-suite.out")"
}

stage_detekt() {
    "${GRADLE[@]}" detekt || return 1
    detail "0 findings over $(git ls-files '*.kt' | wc -l) Kotlin files (detekt.yml)"
}

stage_format() {
    "${GRADLE[@]}" spotlessCheck && detail "ktlint clean (Kotlin + Gradle KTS)"
}

stage_lint() {
    "${GRADLE[@]}" :app:lintDebug || return 1
    detail "$(grep -c '<issue$' app/build/reports/lint-results-debug.xml) issue(s), warnings are errors"
}

stage_tests() {
    "${GRADLE[@]}" :detection:test :core:test :tools:test :app:testDebugUnitTest \
        :app:koverXmlReportAll :app:koverHtmlReportAll :app:koverVerifyAll || return 1
    detail "$(python3 scripts/report.py tests detection/build/test-results/test core/build/test-results/test tools/build/test-results/test app/build/test-results/testDebugUnitTest) · $(python3 scripts/report.py coverage app/build/reports/kover/reportAll.xml)"
}

stage_apk() {
    "${GRADLE[@]}" :app:assembleDebug :app:assembleRelease || return 1
    detail "$(find app/build/outputs/apk -name '*.apk' -printf '%f %s bytes\n' | sort | awk '{printf "%s%s (%.1f MB)", (NR > 1 ? ", " : ""), $1, $2 / 1048576}')"
}

stage_sbom() {
    "${GRADLE[@]}" :app:cyclonedxDirectBom || return 1
    local out
    out="$(python3 scripts/sbom.py build)" || return 1
    python3 scripts/sbom.py notices --check || return 1
    cp build/sbom/*.cdx.json "${LOG_DIR}/" 2>/dev/null || true
    detail "$(python3 -c 'import json, sys; b = json.load(open(sys.argv[1])); print(len(b["components"]), "components, CycloneDX", b["specVersion"])' "${out#Wrote }") · About list matches"
}

stage_e2e() {
    if [[ "${E2E_MODE}" == "skip" ]]; then
        detail "skipped by --e2e skip"
        return 4
    fi
    if ! device_attached; then
        detail "no emulator/device attached (start one with scripts/emulator.sh)"
        [[ "${E2E_MODE}" == "required" ]] && return 1
        return 3
    fi
    "${ADB}" shell rm -f '/data/local/tmp/fork-e2e-*.png'
    "${GRADLE[@]}" :app:connectedDebugAndroidTest
    local result=$?
    # Failure screenshots taken by the instrumented tests become pipeline artifacts.
    local shot
    for shot in $("${ADB}" shell ls /data/local/tmp/ 2>/dev/null | tr -d '\r' | grep '^fork-e2e-.*\.png$'); do
        "${ADB}" pull "/data/local/tmp/${shot}" "${LOG_DIR}/${shot}" >/dev/null
    done
    # Failing instrumented runs still write results, so the counts stay informative.
    detail "$(python3 scripts/report.py tests app/build/outputs/androidTest-results/connected) on $("${ADB}" shell getprop ro.product.model | tr -d '\r')"
    return "${result}"
}

stage_docker() {
    docker_ready || return $?
    scripts/docker-smoke.sh && detail "image built; APK, checksum and license served"
}

stage_open() {
    local report="app/build/reports/kover/htmlAll/index.html"
    if [[ "${OPEN_REPORTS}" == false || -n "${CI:-}" ]]; then
        detail "suppressed (--noOpen or CI)"
        return 4
    fi
    local opener
    for opener in "${FORK_REPORT_BROWSER:-}" xdg-open open; do
        if [[ -n "${opener}" ]] && command -v "${opener}" >/dev/null 2>&1; then
            "${opener}" "${report}" >/dev/null 2>&1 &
            disown || true
            detail "${report} handed to ${opener}"
            return 0
        fi
    done
    detail "open manually: ${report}"
    return 3
}

stage_launch() {
    if [[ "${RUN_APP}" == false ]]; then
        detail "suppressed by --noRun"
        return 4
    fi
    if ! device_attached; then
        detail "no emulator/device attached"
        return 4
    fi
    "${ADB}" install -r -g app/build/outputs/apk/debug/app-debug.apk &&
        "${ADB}" shell am start -W -n "${APP_ID}/.MainActivity" | grep -q "Status: ok" &&
        detail "debug APK installed and started" && return 0
    detail "install/launch failed; does not affect the result"
    return 3
}

parse_arguments() {
    while [[ "$#" -gt 0 ]]; do
        case "$1" in
            --noRun) RUN_APP=false; shift ;;
            --noOpen) OPEN_REPORTS=false; shift ;;
            --e2e|--docker)
                if [[ "$#" -lt 2 || ! "$2" =~ ^(auto|required|skip)$ ]]; then
                    error "$1 requires auto, required or skip."
                    print_usage
                    exit 2
                fi
                if [[ "$1" == "--e2e" ]]; then E2E_MODE="$2"; else DOCKER_MODE="$2"; fi
                shift 2
                ;;
            --report-dir)
                if [[ "$#" -lt 2 || -z "$2" ]]; then
                    error "--report-dir requires a path."
                    print_usage
                    exit 2
                fi
                REPORT_DIR="$2"
                shift 2
                ;;
            --help|-h) print_usage; exit 0 ;;
            *) error "Unknown argument: $1"; print_usage; exit 2 ;;
        esac
    done
}

prepare_log_dir() {
    if [[ -n "${REPORT_DIR}" ]]; then
        LOG_DIR="$(realpath -m "${REPORT_DIR}")"
        REMOVE_LOG_DIR=false
    else
        LOG_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fork-pipeline-XXXXXX")"
    fi
    mkdir -p "${LOG_DIR}"
    rm -f "${LOG_DIR}"/*.log "${LOG_DIR}/summary.txt" "${LOG_DIR}/details"
    trap 'if [[ "${REMOVE_LOG_DIR}" == true ]]; then rm -rf "${LOG_DIR}"; fi' EXIT
    {
        printf 'version=%s\n' "$(cat VERSION)"
        printf 'build=%s\n' "$(cat BUILD_NUMBER)"
        printf 'commit=%s\n' "$(git rev-parse HEAD 2>/dev/null || true)"
        printf 'generated_at=%s\n' "$(date --utc +'%Y-%m-%dT%H:%M:%SZ')"
        printf 'github_run_id=%s\n' "${GITHUB_RUN_ID:-local}"
        printf 'java=%s\n' "$(java -version 2>&1 | head -n 1)"
        printf 'python=%s\n' "$(python3 --version 2>&1)"
        printf 'android_home=%s\n' "${ANDROID_HOME}"
    } > "${LOG_DIR}/environment.txt"
}

print_summary() {
    {
        printf '\n========== Local Pipeline Summary (%s) ==========\n' "$(cat VERSION)"
        printf '%s\n' "${SUMMARY_LINES[@]}"
        printf '=====================================================\n'
    } | tee "${LOG_DIR}/summary.txt"
    [[ "${REMOVE_LOG_DIR}" == false ]] && log "Stage logs and summary kept in ${LOG_DIR}"
}

main() {
    parse_arguments "$@"
    cd "${ROOT_DIR}" || exit 1
    prepare_log_dir

    stage "Models" yes "" stage_models
    stage "ShellCheck" yes "" stage_shellcheck
    stage "Python" yes "" stage_python
    stage "Whitespace" yes "" stage_whitespace
    stage "Lint Suite" yes "" stage_lint_suite
    stage "Format" yes "" stage_format
    stage "Detekt" yes "" stage_detekt
    stage "Android Lint" yes "Models" stage_lint
    stage "Unit Tests" yes "Models" stage_tests
    stage "APK Build" yes "Models" stage_apk
    stage "SBOM" yes "APK Build" stage_sbom
    stage "E2E" yes "APK Build" stage_e2e
    stage "Docker" yes "APK Build,SBOM" stage_docker
    stage "Open Coverage" no "Unit Tests" stage_open
    stage "Launch App" no "APK Build" stage_launch

    local name exit_code=0
    for name in "${MANDATORY[@]}"; do
        # A prerequisite SKIP always follows an upstream FAIL, so FAIL alone decides.
        [[ "${STATUS[${name}]}" == "FAIL" ]] && exit_code=1
    done
    if [[ "${exit_code}" -eq 0 ]]; then
        log "localPipeline.sh completed successfully"
    else
        error "localPipeline.sh completed with failing mandatory stage(s)"
    fi
    print_summary
    exit "${exit_code}"
}

main "$@"
