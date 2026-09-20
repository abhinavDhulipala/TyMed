# TyMed v1 (Android) — release checklist

Scope: Android only for v1, 100% local data (no backend). iOS is a later milestone.

## Done in this repo

- [x] Core app: medications, schedules, Today/Medications/Settings tabs, calendar with
      per-day adherence dials, tap-through day history
- [x] Native non-stop alarm system (foreground service + full-screen activity), verified
      end-to-end on-device: rings, snoozes, escalates, marks taken
- [x] Automated tests (`npm test`) for date/calendar utilities
- [x] Typecheck script (`npm run typecheck`)
- [x] CI (`.github/workflows/ci.yml`) — typecheck + test on every push/PR
- [x] Crash reporting wired (`@sentry/react-native`), no-ops until a DSN is configured
- [x] Privacy Policy + Terms of Use (`legal/`), published copy linked from in-app Settings
- [x] In-app medical disclaimer (Settings screen)
- [x] Play Store listing copy (`store/listing.md`)
- [x] Play Console Data Safety / permission-declaration answers (`store/DATA_SAFETY.md`)

## Still needed — requires your own accounts/credentials, not something I can do

1. **Fill in placeholders**
   - `[support email]` in `legal/PRIVACY_POLICY.md`, `legal/TERMS_OF_USE.md`, and the published
     artifact — pick a real contact address before submitting.
   - `[effective date]` in both legal docs.
   - Host the legal pages somewhere you control long-term (the published Claude artifact link
     works for launch, but a domain you own is more durable) — same content is already in
     `legal/*.md` if you want to render it elsewhere.

2. **Signing & build**
   - Create a Google Play Console account ($25 one-time).
   - Generate a real production keystore (don't reuse the debug one) and set up
     `eas build --platform android --profile production`, or configure Gradle signing
     manually if not using EAS.
   - Set `EXPO_PUBLIC_SENTRY_DSN` (and optionally the Sentry org/project auth token for source
     map upload) in your build environment if you want crash reporting live for v1.

3. **Play Console setup**
   - Create the app listing, paste in `store/listing.md` copy.
   - Complete the Data Safety form and the Alarms & Reminders permission declaration using
     `store/DATA_SAFETY.md`.
   - Upload screenshots (see the list in `store/listing.md`) and an app icon/feature graphic.
   - Complete the content rating questionnaire.
   - Set the Privacy Policy URL field.

4. **Real-device testing beyond the emulator**
   - Test the alarm on at least one real device, ideally one from a manufacturer known for
     aggressive battery optimization (Samsung, Xiaomi, OnePlus) — confirm the alarm still rings
     when the app has been backgrounded for a while. You may need to prompt users to disable
     battery optimization for TyMed (not yet built — a good v1.1 follow-up).
   - Confirm the alarm behaves correctly across a device reboot once you're comfortable adding
     a `BOOT_COMPLETED` receiver (documented as a known gap in the alarm module).

5. **Submit for review**
   - `eas submit` (or manual upload) once the signed build and store listing are ready.
   - Play Store review is typically a few hours to a few days; medical-adjacent apps can take
     longer or get follow-up questions — the Data Safety answers above should pre-empt most of
     them.

## Explicitly out of scope for v1 (by your instruction)

- iOS build/testing
- Cloud sync / account system / data backup
