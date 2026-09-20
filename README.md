# TyMed

A pill-tracking and medication-reminder app, inspired by Pillo's core idea: reminders that actually
get your attention and stay out of the way once you've taken your dose. Android-first, local-only
storage for v1 — iOS support is planned for later.

## Features

- **Medications & recurring schedules** — add a medication with one or more reminder times, and
  choose which days of the week each one repeats on (defaults to every day).
- **Non-stop alarms (Android)** — reminders ring continuously and take over the screen, even when
  locked, until you respond. Snoozing re-rings after a configurable interval, repeating until the
  dose is marked taken. Implemented as a small local native module (`modules/tymed-alarm`) since
  this isn't possible with a plain scheduled notification.
- **Today & history** — the home screen is just today's doses; every calendar day is the same view.
  Tap any day to see what was scheduled, and tap any dose to mark it taken/skipped or correct the
  recorded time.
- **Adherence calendar** — a month view with a per-day ring showing the taken percentage.
- **Local-only storage** — all data lives on-device in SQLite (`expo-sqlite`). Nothing leaves the
  device.

## Tech stack

- [Expo](https://docs.expo.dev/versions/v57.0.0/) SDK 57 + Expo Router, TypeScript, React Native 0.86
- `expo-sqlite` for local persistence
- `expo-notifications` as the iOS reminder fallback (Android uses the native alarm module instead)
- A local Expo native module (`modules/tymed-alarm`, Kotlin) for the Android alarm system
- `@sentry/react-native` for error observability (no-ops unless `EXPO_PUBLIC_SENTRY_DSN` is set)

## Getting started

This project uses a custom **dev client**, not Expo Go — the native alarm module requires it.

```bash
npm install
npx expo run:android   # builds and installs the dev client, requires Android SDK + JDK 17
```

Requires `ANDROID_HOME` and `JAVA_HOME` set. Once the dev client is installed, `npx expo start`
is enough for subsequent JS-only changes; a native (Kotlin) change needs another `expo run:android`.

### Checks

```bash
npm run typecheck
npm test
```

## Project layout

- `app/` — Expo Router screens (Today, Medications, Settings, day/dose detail routes)
- `src/db/` — SQLite schema, migrations, and query helpers
- `src/notifications/` — reminder scheduling (native alarms on Android, `expo-notifications` on iOS)
- `modules/tymed-alarm/` — the native Android alarm module (foreground service + full-screen
  lock-screen activity + self-rescheduling alarm chain)
- `src/components/`, `src/hooks/`, `src/utils/` — UI, data hooks, and pure helpers
- `legal/`, `store/` — privacy policy, terms of use, and Play Store listing/release docs

## Status

Local-only Android v1. See `store/RELEASE_CHECKLIST.md` for what's left before a Play Store
submission.
