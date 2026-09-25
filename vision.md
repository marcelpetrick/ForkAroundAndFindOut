# Vision-Based Elbow-on-Table Detection System

## 1. Project Objective

The goal is to build a system that continuously observes people seated at a dining table and detects whether one or both elbows are resting on the tabletop.

The system should operate automatically so that repeated verbal reminders are unnecessary. When an elbow-on-table posture is detected with sufficient confidence and duration, the system should generate a configurable visual and/or audible warning. The warning should automatically stop when the posture is corrected.

The initial target platform should be an Android smartphone, ideally integrated into an existing Flutter/Dart application. A Raspberry Pi-based implementation remains a viable later option for a permanently installed or multi-camera system.

The key technical problem is not basic pose estimation. Modern frameworks already provide robust human body landmark detection. The difficult part is determining whether an elbow is genuinely resting on the table rather than merely moving over, approaching, or temporarily crossing the tabletop region.

---

# 2. Recommended Overall Approach

The recommended architecture is:

```text
                   ┌────────────────────────┐
CameraX frames ───►│ MediaPipe Pose         │
                   │ Landmarker             │
                   │ 33 landmarks/person    │
                   └───────────┬────────────┘
                               │
                       pose landmarks
                               │
                               ▼
                   ┌────────────────────────┐
                   │ Person/seat tracker    │
                   │ left/right arm state   │
                   └───────────┬────────────┘
                               │
                               ▼
                ┌─────────────────────────────┐
                │ Table-relative feature     │
                │ extraction                 │
                │                             │
                │ • elbow ↔ table position   │
                │ • wrist/shoulder geometry  │
                │ • elbow angle              │
                │ • movement / dwell time    │
                │ • optional depth           │
                │ • optional elbow crop      │
                └─────────────┬───────────────┘
                              │
                              ▼
                ┌─────────────────────────────┐
                │ Elbow-contact classifier   │
                │ P(elbow resting on table)  │
                └─────────────┬───────────────┘
                              │
                              ▼
                    temporal state machine
                  OK → SUSPECT → VIOLATION
                              │
                              ▼
                    visual/audio warning
```

The main recommendation is:

> Use a pretrained pose-estimation model to locate body joints. Do not train a pose model from scratch. Build our own lightweight classifier that decides whether each detected elbow is actually resting on the tabletop.

---

# 3. Smartphone Versus Raspberry Pi

## 3.1 Android Smartphone — Recommended Initial Platform

For the first implementation, an Android smartphone is the strongest choice.

Advantages include:

- integrated high-quality camera;
- autofocus and exposure control;
- CPU and GPU acceleration;
- integrated display;
- integrated speaker;
- battery backup;
- Wi-Fi;
- mature CameraX software stack;
- mature Android ML ecosystem;
- simple packaging and deployment;
- no additional hardware assembly;
- direct compatibility with an existing Flutter app.

The recommended first implementation is therefore:

```text
Android phone
    ↓
Flutter UI
    ↓
Native Kotlin vision module
    ↓
CameraX + MediaPipe
```

## 3.2 Raspberry Pi

A Raspberry Pi implementation remains viable, especially for a permanent installation.

A later system could use:

```text
Raspberry Pi 5
+
Camera Module 3 Wide
+
AI HAT+ 13 or 26 TOPS
+
small speaker/buzzer
+
optional display / status LEDs
```

Raspberry Pi becomes particularly attractive if the system later requires:

- multiple cameras;
- external sensors;
- a fixed installation;
- hardware buttons;
- dedicated always-on operation;
- hardware acceleration via Hailo AI modules.

### Platform Comparison

| Requirement | Android phone | Raspberry Pi 5 |
|---|---|---|
| Initial development | Best | More work |
| Camera quality | Excellent | Excellent |
| Integrated display/audio | Yes | Additional hardware |
| Pose software ecosystem | Excellent | Good |
| Packaging | Very easy | Appliance setup required |
| Permanent installation | Good | Excellent |
| External cameras | Limited | Excellent |
| Multi-camera future | Difficult | Better |
| Hardware work | Minimal | Significant |
| Existing Flutter integration | Ideal | Poorer fit |

### Recommendation

```text
Prototype and probably production v1:
    Android smartphone

Permanent installation / multi-camera v2:
    Raspberry Pi
```

---

# 4. Pose Estimation Framework Options

## 4.1 MediaPipe Pose Landmarker — Recommended

MediaPipe Pose Landmarker is the preferred starting point.

It provides 33 body landmarks, including:

```text
left shoulder       11
right shoulder      12
left elbow          13
right elbow         14
left wrist          15
right wrist         16
```

It also provides:

- 2-D image coordinates;
- 3-D world landmarks;
- visibility/confidence values;
- live-stream processing;
- tracking between frames;
- multiple-pose detection;
- optional segmentation masks;
- Android CPU/GPU delegates.

This makes it a strong fit for detecting several seated people around one table.

## 4.2 Google ML Kit Pose Detection

ML Kit Pose Detection is also technically strong.

Advantages:

- 33 pose landmarks;
- real-time operation;
- straightforward Android integration;
- community Flutter wrappers exist.

However, two disadvantages are important:

1. Pose Detection remains marked as Beta.
2. It detects only one person per image.

That limitation makes it unsuitable for a dining table with multiple children or adults visible at once.

## 4.3 MoveNet

MoveNet Lightning and Thunder are also mature and efficient.

However, MoveNet provides 17 landmarks rather than 33.

Because this application benefits from detailed upper-body geometry and MediaPipe already provides multi-person support and a richer landmark model, MediaPipe is the better default choice.

---

# 5. Correct Formulation of the Vision Problem

The problem is not simply:

> Is the elbow at table height?

It is also not:

> Is the elbow geometrically inside the projected tabletop?

For example, a person may reach across the table:

```text
shoulder
   \
    \
     ELBOW  ───── wrist → serving bowl
       ↓
     table
```

The elbow may temporarily pass over the table without resting on it.

Therefore the useful semantic states are closer to:

```text
A. elbow clearly away from table
B. elbow interacting with / passing over table
C. elbow supported by table
```

Only state C should generate a warning.

This distinction should drive the design.

---

# 6. Camera Position

Camera placement will strongly affect reliability.

A camera directly at table height is not recommended because the table edge can hide the elbow-table interaction.

A better position is:

- somewhat above table/head level;
- diagonal to the dining table;
- looking downward at an oblique angle;
- positioned so shoulders, elbows, wrists, and tabletop are all visible;
- preferably from one end or corner of the table.

Conceptually:

```text
      PHONE
        \
         \
          \       person
           \        O
            \      /|\
             \____/_|_\____
                  TABLE
```

The exact placement should be experimentally validated early in development.

---

# 7. Table Calibration

Automatic table detection is unnecessary for the first implementation.

Because the environment is fixed, manual calibration is simpler and more reliable.

During setup, the app should display the camera image and allow the user to tap approximately four tabletop corners.

Example:

```text
+---------------------------------+
|                                 |
|          camera preview         |
|                                 |
|       x----------------x        |
|       |                |        |
|       |     TABLE      |        |
|       |                |        |
|       x----------------x        |
|                                 |
+---------------------------------+
```

The resulting polygon is stored as the table region.

If the phone is moved significantly, the user should recalibrate.

Optional seating zones can also be configured:

```text
                  Seat 1

          +------------------+
          |                  |
 Seat 4   |      TABLE       | Seat 2
          |                  |
          +------------------+

                  Seat 3
```

This allows stable assignment of detected poses without face recognition.

---

# 8. Person Assignment and Tracking

Pose results from successive frames should not be assumed to preserve stable person ordering.

A lightweight tracking layer should therefore associate detected poses with logical seats.

A good feature for assignment is the torso center:

```text
torsoCentre =
    mean(leftShoulder,
         rightShoulder,
         leftHip,
         rightHip)
```

This torso center can be matched against configured seat zones.

This avoids:

- face recognition;
- identity databases;
- personal biometric classification.

For a fixed dining environment, seat association is simpler and more privacy-preserving.

---

# 9. Detection Features

For every person and every arm, the system should use shoulder, elbow, and wrist landmarks.

## 9.1 Position Features

For each arm:

```text
shoulder.x, shoulder.y
elbow.x, elbow.y
wrist.x, wrist.y
```

Coordinates should be normalized relative to body scale and/or image size.

## 9.2 Table-Relative Features

Important features include:

```text
signedDistance(elbow, tablePolygon)
signedDistance(wrist, tablePolygon)
```

This helps distinguish:

- outside the table;
- near the edge;
- inside the projected tabletop region.

## 9.3 Arm Geometry

Calculate the elbow joint angle:

```text
elbowAngle =
    angle(shoulder, elbow, wrist)
```

Other useful geometric features:

```text
upper-arm angle
forearm angle
wrist-to-elbow vertical difference
elbow-to-shoulder vertical difference
shoulder-elbow distance
elbow-wrist distance
```

## 9.4 Torso Geometry

Useful posture indicators can include:

```text
shoulder tilt
torso tilt
head/shoulder relationship
body lean direction
```

A person resting on an elbow often changes torso posture.

## 9.5 Motion Features

Motion is especially useful.

Across a short temporal window, for example 0.5 to 1.5 seconds, calculate:

```text
mean elbow position
variance of elbow position
mean elbow velocity
maximum elbow velocity
movement direction
```

A reaching elbow is usually moving.

A resting elbow is usually relatively stationary.

---

# 10. Classification Strategy

The classifier should be developed incrementally.

## 10.1 Version A — Deterministic Rules

The first usable detector can be rule-based.

Conceptually:

```text
candidate =
    elbowIsNearTable &&
    confidenceIsHigh

supportedEvidence =
    candidate &&
    elbowVelocityIsLow &&
    armGeometryLooksSupported
```

This approach is useful because it:

- produces an initial system quickly;
- exposes which features matter;
- generates data for later training;
- makes debugging easy.

Because the deployment environment is constrained, a rule-based detector may already work surprisingly well.

## 10.2 Version B — Learned Landmark Classifier

Once training examples are available, replace or augment hand-selected thresholds with:

```text
P(resting | features)
```

Potential feature vector:

```text
[
 elbow/table distance,
 wrist/table distance,

 shoulder-elbow distance,
 elbow-wrist distance,

 elbow angle,
 upper-arm angle,
 forearm angle,

 elbow velocity,
 elbow variance,

 shoulder tilt,
 torso inclination,

 elbow visibility,
 wrist visibility,

 previous temporal values...
]
```

Good initial model types include:

- logistic regression;
- gradient-boosted trees;
- a small multilayer perceptron.

A large neural network is not initially necessary.

## 10.3 Version C — Local Image Classifier

If landmarks alone are insufficient, add a second-stage image classifier.

Once MediaPipe identifies the elbow position, crop a small local image region around the elbow.

Example:

```text
+--------------------+
|                    |
| sleeve             |
|       elbow        |
|        \           |
|=========TABLE======|
|                    |
+--------------------+
```

A small CNN could classify:

```text
NOT_ON_TABLE
ON_TABLE
UNCERTAIN
```

The advantage is that pose estimation already solved localization. The second classifier only needs to understand the local elbow-table interaction.

A mature version could combine:

```text
landmark classifier probability
            +
elbow image classifier probability
            +
temporal evidence
            =
final violation probability
```

This should only be implemented if the simpler landmark-based approach proves insufficient.

---

# 11. Temporal State Machine

A warning must never be generated from a single frame.

A state machine should provide temporal integration and hysteresis.

Example:

```text
NORMAL
  │
  │ P(contact) > 0.70
  ▼
SUSPECT
  │
  │ remains > 0.70 for 0.8 s
  ▼
VIOLATION
  │
  │ P(contact) < 0.35 for 0.5 s
  ▼
NORMAL
```

This prevents rapid oscillation from noisy probabilities such as:

```text
0.68
0.72
0.69
0.73
0.68
```

The following parameters should be configurable:

```text
triggerProbability
clearProbability
triggerDelay
clearDelay
cooldown
```

---

# 12. Per-Elbow State

Each arm should be tracked independently.

Example:

```text
Seat 1
    left elbow: OK
    right elbow: VIOLATION

Seat 2
    left elbow: UNKNOWN
    right elbow: OK
```

A dedicated `UNKNOWN` state is important.

If the elbow is occluded or pose confidence is poor, the system must not interpret missing information as a violation.

The preferred policy is:

> Alarm only on positive evidence.

---

# 13. Alarm Behaviour

The alarm system should be separated from the detection logic.

Possible settings:

```text
Visual warning:
    off
    red border
    red exclamation mark
    full red screen
    slow pulse

Audio:
    off
    beep once
    repeat every N seconds
    continuous tone

Volume:
    configurable

Trigger delay:
    configurable

Grace period:
    configurable
```

A rapidly flashing full-screen red warning is not recommended.

A static red perimeter plus an audible signal is a safer and less intrusive default.

Example:

```text
╔══════════════════════════════════╗
║                                  ║
║           ⚠  ELBOW              ║
║                                  ║
║                                  ║
╚══════════════════════════════════╝
```

The warning should disappear automatically when the violation clears.

---

# 14. Functional Requirements

## FR-01 Camera

The application shall continuously acquire frames from a selected rear-facing camera.

## FR-02 On-Device Processing

Camera images shall be processed locally without requiring cloud connectivity.

## FR-03 Multi-Person Pose Detection

The application shall detect a configurable number of simultaneous people.

Initial target:

```text
1–4 persons
```

## FR-04 Arm Landmarks

For every detected person, the application shall maintain:

```text
left shoulder
left elbow
left wrist

right shoulder
right elbow
right wrist
```

including confidence/visibility values.

## FR-05 Table Calibration

The user shall be able to define the visible tabletop region during setup.

Calibration shall persist between sessions.

## FR-06 Seating Zones

Optional seat regions shall associate detected poses with stable logical seats.

## FR-07 Per-Elbow State

Each elbow shall have one of the following states:

```text
UNKNOWN
CLEAR
SUSPECT
VIOLATION
```

## FR-08 Temporal Filtering

No alarm shall be generated from a single inference frame.

## FR-09 Warning

A violation shall generate configurable visual and/or audible feedback.

## FR-10 Automatic Clearing

Warnings shall automatically cease after the offending posture is no longer detected for a configurable interval.

## FR-11 Manual Pause

The user interface shall include an immediate pause function.

Example use cases:

- serving food;
- rearranging the table;
- temporary unusual activity.

## FR-12 Debug Overlay

Developer/setup mode shall display:

```text
skeleton
landmark confidence
table polygon
seat assignment
elbow state
classifier score
FPS
inference latency
```

## FR-13 Configuration

Persist at least:

```text
alarm modes
alarm volume
trigger delay
clear delay
sensitivity
table calibration
seat zones
camera choice
model choice
```

## FR-14 No Recording by Default

Normal monitoring mode shall not store camera frames.

## FR-15 Training Mode

An explicitly enabled training mode may record labelled samples locally.

## FR-16 Statistics

Optionally store non-image statistics such as:

```text
session duration
number of detected violations
false-alarm correction events
average detection confidence
```

---

# 15. Nonfunctional Requirements

Suggested initial targets:

| Property | Target |
|---|---|
| Analysis rate | ≥10 FPS |
| Preferred rate | 15–30 FPS |
| Camera resolution | 720p initial target |
| Frame backlog | Zero |
| Pose processing | On-device |
| Internet dependency | None |
| False alarm objective | <1 per ordinary meal |
| Sustained violation detection | >95% for violations lasting >2 s |
| UI responsiveness | unaffected by inference |
| Camera processing | background thread |
| Failure policy | uncertainty means no alarm |

The main practical metric should not be frame-level accuracy.

The better product metric is:

> How often does the system incorrectly generate a warning during a normal meal?

---

# 16. Flutter and Android Architecture

The recommended architecture is:

```text
Flutter
│
├── screens
├── settings
├── calibration UI
├── alarm UI
├── state management
│
└── ElbowDetectorPlugin
         │
         │ platform interface
         ▼
      Kotlin / Android
         │
         ├── CameraX
         ├── MediaPipe Tasks
         ├── pose tracking
         ├── feature extraction
         └── classification
```

Flutter should receive small detection events rather than raw video frames.

Example event:

```json
{
  "seat": 2,
  "left": {
    "state": "violation",
    "confidence": 0.91
  },
  "right": {
    "state": "clear",
    "confidence": 0.96
  }
}
```

---

# 17. Why Kotlin Should Own the Camera Pipeline

Flutter provides a camera plugin, but this use case requires continuous native image analysis.

A less desirable pipeline would be:

```text
Camera
 ↓
Flutter CameraImage
 ↓
Dart
 ↓
Platform Channel
 ↓
Kotlin
 ↓
MediaPipe
```

This creates unnecessary image copying and conversion.

A better design is:

```text
CameraX
  ├── Preview → Flutter texture
  │
  └── ImageAnalysis → MediaPipe
```

Only pose results and classifier outputs should cross the Flutter/native boundary.

CameraX should use a latest-frame strategy so inference never builds up a backlog.

---

# 18. Initial MediaPipe Configuration

Initial configuration could conceptually be:

```text
runningMode = LIVE_STREAM
numPoses = 4

minPoseDetectionConfidence ≈ 0.5–0.7
minPosePresenceConfidence ≈ 0.5–0.7
minTrackingConfidence ≈ 0.5–0.7

outputSegmentationMasks = false initially
delegate = GPU if stable
```

Start with the full Pose Landmarker model and benchmark:

```text
lite
full
heavy
```

The heavy model should only be used if it meaningfully improves elbow tracking on the actual device.

---

# 19. Partial-Body Visibility Risk

Dining-table scenes create a specific pose-estimation challenge.

People may appear like:

```text
   head
 shoulders
 arms
-----------
   TABLE
-----------
```

while hips and legs are partially or completely hidden.

Therefore the first proof-of-concept should answer one question:

> With the intended camera placement, can MediaPipe consistently track both elbows for all seated people?

If the answer is yes, the project becomes much simpler.

If not, camera position should be changed before developing sophisticated classifiers.

---

# 20. Optional Depth Sensing

Android provides a possible future enhancement through ARCore Depth.

A calibrated tabletop could be modelled as a plane:

```text
Ax + By + Cz + D = 0
```

The system could then estimate the distance between the elbow region and the tabletop plane.

Conceptually:

```text
distance(elbow_surface, tabletop_plane)
```

Because pose landmarks represent joints rather than physical contact points, a resting elbow will still be several centimetres above the mathematical table surface.

Therefore a learned tolerance would be required.

Depth should remain optional because:

- support varies by device;
- hardware depth sensors are not universal;
- monocular depth-from-motion is less useful when the phone is stationary.

Recommended order:

```text
RGB pose + temporal classifier
           first

Depth information
           optional enhancement
```

---

# 21. Training Data Strategy

A training mode should allow explicit labels such as:

```text
[ NORMAL ]
[ LEFT ELBOW ]
[ RIGHT ELBOW ]
[ BOTH ]
```

Training examples should cover:

```text
normal knife/fork use
drinking
cutting food
reaching for food
passing food
leaning forward
hands under table
hands above table
forearms resting correctly
one elbow resting
two elbows resting
chin in hand with elbow resting
long sleeves
short sleeves
different chairs
different lighting
plates/bowls blocking arms
other people passing in front
```

The most valuable negative samples are not ordinary correct poses.

They are cases that look similar to violations but are still acceptable.

---

# 22. Dataset Splitting

Do not randomly split individual video frames into training and test sets.

Adjacent frames are nearly identical.

A random frame split would produce misleadingly optimistic evaluation results.

Instead split by recording session:

```text
Meal/session 1 → training
Meal/session 2 → training
Meal/session 3 → validation
Meal/session 4 → test
```

Whole sessions, lighting conditions, and seating configurations should be held out whenever possible.

---

# 23. Privacy Design

Because the application observes children and family members during meals, privacy should be designed conservatively.

Normal operation should be:

```text
camera frame
    ↓
RAM
    ↓
inference
    ↓
discard immediately
```

Avoid by default:

```text
cloud upload
face recognition
identity database
continuous video archive
```

Training recordings should require explicit opt-in and should remain deletable.

CI regression tests should preferably use:

- extracted pose-feature JSON;
- non-sensitive synthetic fixtures;
- deliberately prepared test images;
- anonymized or consented samples.

Real family video should not be committed to GitHub.

---

# 24. CI/CD

A GitHub Actions pipeline can include:

```text
dart format check
flutter analyze
flutter test

Kotlin unit tests

classifier unit tests
state-machine tests

build Android APK
```

The build pipeline can produce:

```text
app-debug.apk
```

as a downloadable CI artifact.

Vision regression tests should use deterministic fixtures such as:

```text
fixtures/
  arms_clear.json
  one_elbow_resting.json
  reaching_for_bowl.json
  ambiguous_occlusion.json
```

These tests can exercise the classifier without requiring a physical camera.

A smaller set of prerecorded non-sensitive clips can be retained separately for end-to-end pose-estimation regression.

---

# 25. Proposed Project Structure

```text
lib/
  app/
  settings/
  calibration/
  monitoring/
  alarms/
  diagnostics/

packages/
  elbow_detector/
    lib/
      elbow_detector.dart

    android/
      src/main/kotlin/.../
        CameraController.kt
        PoseEngine.kt
        PoseTracker.kt
        TableCalibration.kt
        ArmFeatureExtractor.kt
        ElbowClassifier.kt
        TemporalFilter.kt
        DetectorPlugin.kt

models/
  pose_landmarker_full.task

test/
  classifier/
  state_machine/
  calibration/
```

If custom ML is introduced later:

```text
ml/
  notebooks/
  training/
  datasets/
  export/
```

The production model can then be copied into Android assets.

---

# 26. State-Machine Tests

Example no-alarm scenario:

```text
0 ms      probability .20
200 ms    probability .92
400 ms    probability .85
600 ms    probability .30
```

Expected result:

```text
NO ALARM
```

because the event was too brief.

Example sustained violation:

```text
0 ms      .85
200 ms    .91
400 ms    .93
600 ms    .94
800 ms    .92
1000 ms   .95
```

Expected result:

```text
VIOLATION
```

After a sufficient clear interval such as:

```text
.20
.18
.12
```

Expected state:

```text
NORMAL
```

This logic is deterministic and should have extensive unit-test coverage.

---

# 27. False-Alarm Feedback

A useful diagnostic feature is:

```text
[ FALSE ALARM ]
```

This should be intended for the supervising adult, not the child.

When pressed, the system can save:

```text
feature vector + classifier output
```

or, when explicit training mode is active:

```text
a short local video clip
```

A complementary diagnostic action can be:

```text
[ MISSED VIOLATION ]
```

These samples are particularly valuable because they identify classifier weaknesses in the actual deployment environment.

---

# 28. Conservative Initial Alarm Rule

The first usable implementation should favor low false-positive rates.

For example:

```text
P(pose classifier says resting) > 0.75
AND

elbow remains in tabletop interaction region
AND

elbow speed is low
AND

visibility is sufficient
AND

condition lasts > 1 second
```

Only then:

```text
ALARM
```

A system that misses brief contacts but rarely alarms incorrectly is more usable than one that triggers every few minutes during normal eating.

---

# 29. Initial Acceptance Tests

| Situation | Expected Result |
|---|---|
| Hands holding fork and knife | No alarm |
| Forearms touching table, elbows outside | No alarm |
| Hands temporarily below table | No alarm |
| Reaching across table | No alarm |
| Passing a plate | No alarm |
| Elbow passes over table for <0.5 s | No alarm |
| Left elbow rests on table >2 s | Alarm |
| Right elbow rests on table >2 s | Alarm |
| Both elbows rest | Alarm |
| Elbow leaves table | Alarm clears |
| Elbow cannot be seen | UNKNOWN / no alarm |
| Person leaves seat | No alarm |
| Another person crosses camera | No alarm |
| Bowl partially occludes arm | Prefer UNKNOWN over alarm |

These scenarios should gradually become executable regression fixtures.

---

# 30. Features Not Recommended for Version 1

The initial system should avoid unnecessary complexity.

Do not initially build:

```text
custom pose-estimation network
cloud computer vision
face recognition
automatic identity recognition
automatic table object detection
mandatory ARCore dependency
Raspberry Pi hardware version
hard 30-FPS requirement
full-scene deep neural classifier
```

These features add complexity without addressing the main uncertainty first.

---

# 31. Raspberry Pi Future Architecture

If the smartphone prototype proves successful and a permanent appliance becomes desirable, Raspberry Pi can be reconsidered.

A major benefit is multi-camera support.

For example:

```text
Camera A                    Camera B
     \                        /
      \                      /
       \                    /
        +------------------+
        |      TABLE       |
        +------------------+
```

Two viewpoints make true elbow-table contact much easier to infer and substantially reduce occlusion problems.

This should be considered only after the single-camera smartphone design has been validated.

---

# 32. Development Sequence

## Phase 1 — Pose Feasibility

Build an Android native prototype with:

```text
CameraX
+
MediaPipe Pose Landmarker
+
up to 4 poses
+
skeleton overlay
```

No alarm logic yet.

The purpose is to validate elbow tracking using the intended physical phone position.

## Phase 2 — Flutter Integration

Expose:

```text
camera preview
pose coordinates
seat assignment
FPS
```

through the existing Flutter application.

## Phase 3 — Calibration

Implement:

```text
table polygon
seat regions
configuration persistence
```

## Phase 4 — Rule-Based Elbow Detection

Implement:

```text
table-relative geometry
arm angles
velocity
visibility
temporal state machine
```

Begin real household testing.

## Phase 5 — Data Collection

Collect:

- false positives;
- missed violations;
- explicitly labelled examples.

## Phase 6 — Learned Pose Classifier

Train a compact model from pose-feature windows.

Replace selected hand-tuned thresholds with:

```text
P(elbowSupported)
```

## Phase 7 — Local Image Classifier

Only if required, add elbow-region visual classification for ambiguous events.

## Phase 8 — Optional Depth

Experiment with ARCore depth and tabletop-plane measurements.

## Phase 9 — Dedicated Appliance

If useful, port the validated algorithm to Raspberry Pi.

---

# 33. Recommended Version 1 Technical Stack

```text
Application
    Flutter / Dart

Android integration
    Kotlin

Camera
    CameraX

Pose
    MediaPipe Tasks Vision
    Pose Landmarker Full initially

Inference mode
    LIVE_STREAM

Frame scheduling
    KEEP_ONLY_LATEST

People
    max 4 poses

Calibration
    manually selected table polygon
    manually selected seat zones

Classification v1
    deterministic features
    + temporal hysteresis

Classification v2
    small ML classifier
    trained from pose features

Alarm
    Flutter
    static red border / warning icon
    configurable audio

Storage
    local settings only by default

Networking
    none required

CI
    GitHub Actions
    Flutter analysis/tests
    Kotlin tests
    classifier regression tests
    Android build
```

---

# 34. Core Architectural Decision

The design can be summarized as:

> MediaPipe determines where the body is. Our own lightweight temporal classifier determines whether an elbow is resting on the table.

This is far more tractable than training a large generic model to understand dining etiquette.

The deployment environment is favorable because it is highly constrained:

- fixed table;
- fixed camera;
- fixed chairs;
- small set of people;
- similar viewpoints;
- repeated operating conditions.

A relatively simple personalized classifier may therefore outperform a much larger generic model for this exact task.

---

# 35. Final Recommendation

Start with the smartphone.

Use the existing Flutter application as the UI and configuration layer, while implementing the real-time computer-vision pipeline natively in Kotlin with CameraX and MediaPipe Pose Landmarker.

The first milestone should contain no custom machine learning at all.

It should show:

- the live camera feed;
- detected skeletons;
- the calibrated tabletop;
- seat assignments;
- left/right elbow positions;
- confidence values;
- inference FPS and latency.

Once that is working reliably in the actual dining-room geometry, implement table-relative features and the temporal state machine.

Only after real-world testing should a custom lightweight classifier be trained.

The central technical risk is not whether modern Android hardware can detect elbows. It can. The difficult part is avoiding false alarms when an elbow legitimately approaches, crosses, or moves over the table.

The most promising solution is therefore:

```text
fixed-camera calibration
+
MediaPipe body landmarks
+
table-relative arm geometry
+
motion features
+
temporal hysteresis
+
small personalized classifier
```

That should provide the best balance between reliability, development effort, privacy, and maintainability.

---

# 36. References

- MediaPipe Pose Landmarker for Android: https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android
- MediaPipe Pose Landmarker overview: https://developers.google.com/edge/mediapipe/solutions/vision/pose_landmarker
- MediaPipe Pose Landmark API: https://ai.google.dev/edge/api/mediapipe/python/mp/tasks/vision/PoseLandmark
- CameraX Image Analysis: https://developer.android.com/media/camera/camerax/analyze
- ML Kit Pose Detection: https://developers.google.com/ml-kit/vision/pose-detection
- ML Kit Pose Classification Guidance: https://developers.google.com/ml-kit/vision/pose-detection/classifying-poses
- Flutter camera plugin documentation: https://docs.flutter.dev/cookbook/plugins/picture-using-camera
- MoveNet overview: https://blog.tensorflow.org/2021/08/pose-estimation-and-classification-on-edge-devices-with-MoveNet-and-TensorFlow-Lite.html
- Raspberry Pi AI HAT+: https://www.raspberrypi.com/products/ai-hat/
- Raspberry Pi Camera Software: https://www.raspberrypi.com/documentation/computers/camera_software.html
- ARCore supported devices: https://developers.google.com/ar/devices
- ARCore Raw Depth: https://developers.google.com/ar/develop/java/depth/raw-depth
- GitHub Actions artifacts: https://docs.github.com/en/actions/tutorials/store-and-share-data
- WCAG flashing guidance: https://www.w3.org/WAI/WCAG21/Understanding/three-flashes
