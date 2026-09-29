# TyMed

A pill-tracking and medication-reminder app: reminders that actually get your attention and stay
out of the way once you've taken your dose. Native Android, local-only storage.

## Features

- **Medications & recurring schedules** — add a medication with one or more reminder times, and
  choose whether it repeats daily, on specific days of the week, or monthly.
- **Non-stop alarms** — reminders ring continuously and take over the screen, even when locked,
  until you respond. Snoozing re-rings after a configurable interval, repeating until the dose is
  marked taken. Implemented with `AlarmManager.setAlarmClock` + a foreground service + a
  full-screen lock-screen activity (`com.tymed.app.alarm`) since this isn't possible with a plain
  scheduled notification.
- **Today & history** — the home screen is just today's doses; every calendar day is the same
  view. Tap any day to see what was scheduled, and tap any dose to mark it taken/skipped or
  correct the recorded time.
- **Adherence calendar** — a month view with a per-day ring showing the taken percentage.
- **On-device AI assistant** (opt-in) — chat about your medications ("what's left today?", "add
  ibuprofen twice a day") using Gemini Nano via ML Kit GenAI. Runs fully on-device; nothing is
  sent over the network. Requires Android 8.0+ and a device that supports AICore.
- **Local-only storage** — all data lives on-device in a Room/SQLite database. Nothing leaves the
  device.

## Tech stack

- Kotlin + Jetpack Compose (Material 3) + Navigation-Compose
- Room for local persistence
- `AlarmManager` + a foreground service + a full-screen activity for the non-stop alarm system
- ML Kit GenAI (Gemini Nano / AICore) for the on-device assistant
- Sentry Android for error observability (no-ops unless a `SENTRY_DSN` is configured)

## Getting started

The whole project lives under `android/` as a normal Android Studio project — open that folder
in Android Studio, or build from the command line:

```bash
cd android
./gradlew assembleDebug     # builds app-debug.apk
./gradlew installDebug      # builds and installs on a connected device/emulator
```

Requires JDK 17 and the Android SDK (`local.properties` with `sdk.dir=...`, or `ANDROID_HOME` set
— Android Studio creates `local.properties` for you automatically).

### Checks

```bash
cd android
./gradlew testDebugUnitTest        # fast local unit tests (Robolectric-backed where needed)
./gradlew connectedDebugAndroidTest # instrumented tests — needs a device/emulator; includes the
                                     # Room migration adoption test (see AGENTS.md)
./gradlew lintDebug
```

## Project layout

Everything is under `android/app/src/main/java/com/tymed/app/`:

- `data/` — Room entities/DAOs/repositories, the shared recurrence-rule evaluator, and the
  `AlarmScheduler` interface the alarm subsystem implements
- `alarm/` — the native alarm system: `AlarmManager` scheduling, the ringing foreground service,
  the full-screen lock-screen activity, and `BootReceiver` for reboot recovery
- `ai/` — the on-device assistant: prompt building, tool dispatch, and the confirmation state
  machine that decides what's allowed to actually write data
- `ui/` — Compose screens, components, and ViewModels, organized by feature (`doses/`,
  `medications/`, `settings/`, `assistant/`)
- `legal/`, `store/` — privacy policy, terms of use, and Play Store listing/release docs

## Status

Native Android v1, local-only. See `store/RELEASE_CHECKLIST.md` for the release process and
what's left before a Play Store submission.
