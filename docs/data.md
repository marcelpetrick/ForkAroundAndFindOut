# Local data, session logs and replay

Copyright (C) 2026 Marcel Petrick. SPDX-License-Identifier: GPL-3.0-or-later.

Nothing leaves the phone unless the adult exports it. No image or video is ever stored.

| Store | When written | Content |
| --- | --- | --- |
| `shared_prefs/settings.xml` | Settings changes, calibration | Versioned settings JSON (image-space calibration) |
| `files/samples.json` | *False alarm* / *Missed violation* taps; session end if statistics are on | Feature vectors of all seats with session id; session statistics (duration, violations, false alarms, missed violations, mean joint confidence). Max. 5000 records, atomic writes |
| `files/sessions/*.jsonl.gz` | Only while **training mode** is switched on in adult diagnostics | Session log (below). Max. 100 MB for all logs together |

The *Local data* screen shows counts and sizes, exports the records (JSON) or any log
(gzip) through the system file picker, deletes one log, or deletes everything.

## Session log format (schema 1)

gzip-compressed JSON Lines, sync-flushed every 30 frames and on every label, so a killed
app still leaves a readable log. Coordinates are normalized to the upright camera
analysis image (the app's single coordinate system).

```json
{"type":"header","schema":1,"session":"…","app":"<version>","model":"FULL","people":4,
 "aspect":0.5625,"rotation":90,"table":[[x,y]×4],"seats":[[[x,y]×4]…],
 "timing":{"trigger":0.75,"clear":0.35,"triggerMs":1000,"clearMs":500,"cooldownMs":1500,"maxGapMs":500},
 "imageRecorded":false}
{"type":"frame","t":123456,"aspect":0.5625,"poses":[[x,y,c × 33]…],"arms":[[seat,"L"|"R","STATE",score|null]…]}
{"type":"label","t":123500,"label":"NORMAL|LEFT|RIGHT|BOTH|FALSE_ALARM|MISSED_VIOLATION","seat":1}
```

`t` is the capture time in milliseconds of the phone's uptime clock; `arms` are the states
the phone computed live. Landmarks rather than derived features are stored so that any
future rule or model can be recomputed from the same sessions. Feedback taps use seat 0
(all seats).

## Replay and evaluation

```sh
scripts/replay.sh demo-log /tmp/demo.jsonl.gz 3          # synthetic, labelled session
scripts/replay.sh replay /tmp/demo.jsonl.gz               # evaluate as recorded
scripts/replay.sh replay --trigger-ms 1500 meal-*.jsonl.gz   # tuning experiment
```

Per session the tool re-runs the detector and prints frames, duration, reminder onsets,
reminders within ±5 s (`--window-ms`) of a `FALSE_ALARM`/`NORMAL` label on the same seat,
positive labels (`LEFT/RIGHT/BOTH/MISSED_VIOLATION`) with a matching violation in the
window, the UNKNOWN fraction, and live/replay agreement (after a 2 s warm-up, because a
log switched on mid-session replays from a cold detector). 100 % agreement with unchanged
timing is a regression check: the desktop replays exactly what the phone decided.

Evaluate by **whole sessions** (vision §22): pass training, validation and test sessions
as separate file sets, never frames of one meal in different sets. A learned classifier
(vision §10.2) is only worth training after enough real, consented sessions exist; this
repository contains no family recordings and must never receive any.
