# How Fork Around & Find Out works (C4 architecture)

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

This page explains the app from the outside in, in four steps. Each step zooms in a little
further, following the [C4 model](https://c4model.com/):

1. **Context** – who uses the app and what it talks to.
2. **Containers** – the separately running or shipped pieces.
3. **Components** – the main parts inside the Android app.
4. **Workflows** – what happens, step by step, during setup, a meal, and a release.

The last section is the **everyday workflow for the family**. Read it first if you only want
to use the app. The deeper technical notes live in [architecture](architecture.md),
[detection](detection.md) and [data](data.md).

## The idea in one paragraph

A phone stands at a corner of the dinner table. Its camera sees everyone's arms. A body-pose
model (Google's MediaPipe) finds shoulders, elbows and wrists in each picture. Our own small,
careful rule then asks, for every elbow: *is it resting on the tabletop, and has it stayed
there for about a second?* If yes, the screen shows a red border and a friendly card
("Elbows off the table, please · Blue seat · left"), optionally with a soft chime. When the
elbow lifts, the reminder fades and a short "Thank you!" appears. Pictures never leave the
phone's memory: nothing is saved, nothing is uploaded, nobody is identified.

## 1. Context – the app and its surroundings

```mermaid
flowchart TB
    family["👪 Family at the table<br/>(children and adults)"]
    adult["🧑 Adult who sets it up<br/>(places the phone, changes settings)"]
    app["📱 Fork Around & Find Out<br/>Android app on a phone"]
    dev["🧑‍💻 Developer<br/>(builds, tests, releases)"]
    gh["GitHub<br/>source, CI, releases,<br/>container registry"]

    family -- "is watched by the camera,<br/>sees and hears gentle reminders" --> app
    adult -- "sets up camera and table,<br/>pauses, gives feedback" --> app
    dev -- "pushes code, tags releases" --> gh
    gh -- "signed APK (download once)" --> adult
```

What matters here:

- The app has **no internet permission**. After installation it never talks to anything.
- GitHub is only involved in building and downloading the app, not in running it.

## 2. Containers – the pieces that are built and shipped

```mermaid
flowchart LR
    subgraph phone["📱 Phone (Android 14+)"]
        app["Android app<br/>Kotlin UI, CameraX,<br/>MediaPipe pose model,<br/>local settings and logs"]
    end
    subgraph laptop["💻 Laptop"]
        tools["Replay tool<br/>(:tools, command line)<br/>re-runs detection on<br/>recorded training logs"]
        emulator["Android emulator<br/>(development and tests)"]
    end
    subgraph github["☁️ GitHub"]
        ci["GitHub Actions<br/>runs localPipeline.sh:<br/>lint, tests, e2e, Docker"]
        release["Release page<br/>signed arm64 APK + SHA-256"]
        ghcr["GHCR image<br/>nginx serving the APK"]
    end

    ci --> release
    ci --> ghcr
    ghcr -- "docker run → open on the phone" --> app
    release -- "download and install" --> app
    app -- "exported training log (.jsonl.gz),<br/>only when an adult chooses to" --> tools
    emulator -. "runs the same app" .- app
```

| Container | What it is | Why it exists |
| --- | --- | --- |
| Android app | The product. Everything happens here. | Camera, detection and reminders on the phone itself. |
| Replay tool | A small JVM command-line program. | Tune detection thresholds on recorded meals without a phone. |
| GitHub Actions | The same `localPipeline.sh` that developers run. | Every change is formatted, linted, tested (unit, UI, emulator) and packaged. |
| Release APK and GHCR image | The downloadable app, and a tiny web server that hands it out. | Easy installation on the family phone. |

## 3. Components – inside the Android app

The code is split into four Gradle modules. The two in the middle contain all the decisions
and have no Android code at all, so they are tested quickly and thoroughly on a normal JVM.

```mermaid
flowchart TB
    subgraph app[":app – Android"]
        ui["MainActivity<br/>screens: Welcome, Position, Mark table,<br/>Seats, Monitor, Settings, Data, About"]
        stage["StageView<br/>draws camera overlay, table,<br/>skeletons, warnings, loupe"]
        camera["CameraSession + FrameAnalyzer<br/>CameraX preview and 640×360 analysis,<br/>one frame at a time"]
        engine["MediaPipeEngine<br/>pose landmarks (Full/Lite, CPU/GPU)"]
        speaker["ChimeSpeaker<br/>soft generated chimes"]
        store["LocalStore, SettingsCodec,<br/>SessionRecorder<br/>settings and opt-in logs"]
    end
    subgraph core[":core – plain Kotlin"]
        session["MonitorSession<br/>one meal: grace, reminders, rest,<br/>thank-you, summary"]
        monitor["Monitor + AlarmPolicy<br/>fresh evidence only, sound decisions"]
        vis["VisibilityCheck<br/>10-second placement check"]
        log["SessionLog + Replay"]
    end
    subgraph detection[":detection – plain Kotlin"]
        detector["Detector<br/>per seat, per elbow"]
        geometry["Geometry + arm rule<br/>distances, angles, stillness"]
        seats["SeatTracker + SeatProposal<br/>stable seats, never identities"]
        temporal["TemporalFilter<br/>UNKNOWN → CLEAR → SUSPECT → VIOLATION"]
    end
    tools[":tools – replay command line"]

    camera --> engine --> ui
    ui --> session --> monitor --> detector
    ui --> vis
    detector --> geometry
    detector --> seats
    detector --> temporal
    session -- "MonitorUiState<br/>(what to show, which sound)" --> ui
    ui --> stage
    ui --> speaker
    ui --> store
    store --> log
    tools --> log
```

How to read it: **`:app` only shows things**. It passes camera results down and renders the
answer that comes back. **`:core` decides** what the screen should say and which sound to
play. **`:detection` measures** each elbow.

## 4. Workflows

### 4.1 Setting up (once per table and phone position)

```mermaid
flowchart LR
    w["Welcome"] --> p["Position the phone<br/>placement picture,<br/>people stepper, lens chips"]
    p --> check{"10-second<br/>visibility check<br/>passed?"}
    check -- "no: the screen says why<br/>(nobody, too few, too many,<br/>arms hidden on the left/right,<br/>too slow)" --> p
    check -- "someone was not settled:<br/>Restart the 10-second check" --> check
    check -- yes --> t["Mark table<br/>tap 4 corners, drag to adjust,<br/>loupe magnifies"]
    p -. "Mark table without the check" .-> t
    t --> s["Seats (optional)<br/>Suggest seats or tap them,<br/>live 'inside a seat' count"]
    s --> ready["Ready: Start dinner"]
```

The table outline is stored in the camera image's own coordinates, together with the
picture's shape and rotation. If the lens or orientation changes, the app notices and asks
for a new outline instead of guessing.

### 4.2 What happens with every camera frame (about 10–30 times a second)

```mermaid
sequenceDiagram
    participant Cam as Camera (CameraX)
    participant An as FrameAnalyzer
    participant MP as MediaPipe
    participant UI as MainActivity
    participant MS as MonitorSession
    participant D as Detector

    Cam->>An: new 640×360 frame
    alt previous frame still being analysed
        An-->>An: drop it (counted in diagnostics)
    else free
        An->>MP: upright copy, one frame in flight
        MP-->>UI: body landmarks + capture time
        UI->>MS: frame(poses, time, image shape)
        MS->>D: too old? different shape? → ignore / ask to recalibrate
        D-->>MS: per seat: left and right elbow state
    end
    Note over UI,MS: Separately, every 100 ms a watchdog tick runs,<br/>even when no frames arrive.
    UI->>MS: tick(now)
    MS-->>UI: MonitorUiState (status, reminder card, sound)
    UI->>UI: draw border/card, play or stop the chime
```

The watchdog matters: if the camera stalls, the evidence goes stale and the reminder stops
by itself. **Missing evidence never causes a reminder.**

### 4.3 How one elbow becomes a reminder

```mermaid
stateDiagram-v2
    [*] --> UNKNOWN
    UNKNOWN --> CLEAR: elbow visible
    CLEAR --> SUSPECT: looks supported on the table
    SUSPECT --> CLEAR: lifted again
    SUSPECT --> VIOLATION: still resting after ~1 s
    VIOLATION --> CLEAR: lifted for ~0.5 s (then a short cooldown)
    CLEAR --> UNKNOWN: hidden or data gap
    SUSPECT --> UNKNOWN: hidden or data gap
    VIOLATION --> UNKNOWN: hidden or data gap
```

"Looks supported" means all of these at once: the elbow is on or just inside the table
outline, the arm is bent, the upper arm points down, and the arm is still. Reaching for
the salt, passing a plate or a brief touch does not qualify.

A **reminder** is shown while any elbow is in VIOLATION, except

- during the **start grace** (a few seconds after Start or Resume),
- while **paused** (Pause button, or hold volume-down),
- for 30 seconds after an adult taps **False alarm**,
- when the phone is **too slow** to give trustworthy evidence.

### 4.4 Training data and replay (optional, adults only)

```mermaid
flowchart LR
    on["Diagnostics → Training mode on"] --> rec["During the meal:<br/>body landmarks + labels<br/>(Normal, Left, Right, Both,<br/>False alarm, Missed)"]
    rec --> file["gzip log on the phone<br/>(no pictures)"]
    file --> exp["Local data → Export"]
    exp --> replay["Laptop: scripts/replay.sh replay log.jsonl.gz<br/>reminders, false alarms, recall;<br/>try other thresholds"]
```

### 4.5 From a code change to a public release

```mermaid
flowchart LR
    code["Change + tests"] --> local["./localPipeline.sh<br/>models · ShellCheck · Python ·<br/>whitespace · ktlint · Android lint ·<br/>unit + UI tests (≥95 % coverage) ·<br/>APKs · emulator e2e · Docker"]
    local --> push["git push (main)"]
    push --> ci["GitHub Actions:<br/>same pipeline on an emulator"]
    ci --> tag{"version tag v*?"}
    tag -- no --> artifacts["APKs + reports as artifacts"]
    tag -- yes --> rel["GitHub release:<br/>signed arm64 APK + SHA-256,<br/>GHCR image :version and :latest"]
```

## 5. The everyday workflow for the family

### First time (about five minutes)

1. Install the APK from the GitHub release on an Android 14 (or newer) phone.
2. Put the phone **above head height at a corner of the table**, tilted down. A shelf or a
   small tripod works well. Avoid a bright window behind the table.
3. Open the app and tap **Set up camera**. Allow the camera.
4. Set the number of people with **−/+**. If the phone has several lenses, pick **Wide**.
5. Everyone sits as they would during a meal. The **10-second check** tells you when all
   shoulders, elbows and wrists are visible. If someone was still moving, tap
   **Restart the 10-second check**.
6. **Mark table**: tap the four corners of the tabletop. Drag a corner to fine-tune.
7. **Seats** (optional): tap **Suggest seats** and check that everyone counts as "inside a
   seat", or skip this step.

### Every dinner

1. Put the phone in the same place and tap **Start dinner**. The launcher shortcut
   *Start dinner* (long-press the app icon) does the same.
2. Eat. If an elbow rests on the table for about a second, the screen turns red at the
   edges and shows which seat and side. Lift it and the reminder goes away.
3. Need a break? Tap **Pause**, or hold **volume-down**, without picking up the phone.
4. Wrong reminder? An adult opens **Adult diagnostics** and taps **False alarm**. The app
   goes quiet for 30 seconds.
5. Tap **Stop** at the end. The welcome screen shows a friendly summary: time, number of
   reminders, the longest calm stretch, and reminders per seat colour.

### When something is off

| The app says | What to do |
| --- | --- |
| "Nobody has been visible for a while. Has the phone moved?" | Tap **Recalibrate camera** and mark the table again. |
| "The phone is getting warm" or "Processing is slow" | Tap **Use the Lite model**. Dinner continues. |
| "The camera view changed, so the table outline no longer fits" | Run the setup again; the old outline no longer fits the picture. |
| "Camera permission is needed" | Tap **Open app settings** and allow the camera. |

### What the app does not do

- It does not save or send any picture or video.
- It does not recognise who someone is. Seats are colours (Green, Blue, Orange, Purple).
- It is a gentle helper, not a measurement. When it cannot see an elbow, it stays quiet.
