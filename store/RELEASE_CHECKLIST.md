# TyMed v1 (Android) — release checklist

Scope: Android only, 100% local data (no backend). Native Android (Kotlin + Jetpack Compose) as
of the rewrite off Expo/React Native — see `AGENTS.md` for the current architecture.

## Distribution: direct APK, not the Play Store

For "myself and a few people," a sideloaded APK is simpler than a Play Store listing — no $25
fee, no review process, no Data Safety/content-rating forms. The sections below about Play
Console are kept for later in case you want a public listing eventually, but they're not on the
critical path right now.

### Cutting a release

```
git tag v1.0.1
git push origin v1.0.1
```

Pushing a `v*` tag triggers `.github/workflows/release.yml`, which builds a signed release APK
and attaches it to a new GitHub Release. Whoever you're sharing the app with downloads the APK
from the repo's Releases page and installs it (Android will prompt to enable "install from
unknown sources" for whatever app they downloaded it with, e.g. the browser or Files app).
Because every build is signed with the same release key, installing a new version over an old
one works like a normal update — no need to uninstall first.

Regular commits/pushes to `main` do **not** build or publish anything; only a `v*` tag does.

### ⚠️ Back up the release keystore

`keystores/tymed-release.jks` (plus the passwords in `keystore.properties`) is the one thing
that makes future releases install as *updates* rather than conflicting apps. It's deliberately
never committed to the repo (see `.gitignore`) — GitHub Actions has its own copy in repo secrets
(`RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`)
for CI builds, but **your local machine currently has the only other copy**. If you lose it, you
can still release — you'd just generate a new keystore and everyone would need to uninstall the
old APK first, since Android won't let a differently-signed build install "over" it. Back up
`keystores/tymed-release.jks` and the passwords somewhere durable (password manager, encrypted
backup) — not in this repo, especially once it's public.

### To build a release locally instead of via CI

```
cd android && SENTRY_DISABLE_AUTO_UPLOAD=true ./gradlew assembleRelease
```

Needs a local `keystore.properties` at the repo root (gitignored) — see the top of
`android/app/build.gradle` for the exact fields it reads. Without that file, release builds
silently fall back to debug signing, which still works for testing but shouldn't be shared.

`versionCode`/`versionName` default to whatever's hardcoded in `android/app/build.gradle`; the
release workflow overrides both from the pushed tag via `-PtymedVersionName=... -PtymedVersionCode=...`
(see `.github/workflows/release.yml`) — pass the same properties locally if you want a build that
matches a specific tag.

## Done in this repo

- [x] Core app: medications, schedules, Today/Medications/Settings tabs, calendar with
      per-day adherence dials, tap-through day history
- [x] Native non-stop alarm system (foreground service + full-screen activity), verified
      end-to-end on-device: rings, snoozes, escalates, marks taken
- [x] Recurring doses: daily/weekly/monthly with an optional end date
- [x] Duplicate-medication detection (blocks exact matches, warns on likely-mistake partials)
- [x] Automated tests: local unit tests (`./gradlew testDebugUnitTest`) plus an instrumented Room
      migration adoption test (`./gradlew connectedDebugAndroidTest`) proving existing installs
      upgrade without data loss
- [x] CI: build + unit tests + lint on every push/PR (`ci.yml`); signed release build + GitHub
      Release publish on every `v*` tag (`release.yml`)
- [x] Crash reporting wired (Sentry Android), no-ops until a `SENTRY_DSN` is configured — release
      builds also disable Sentry's source-map upload step until then (it hard-fails without one)
- [x] Privacy Policy + Terms of Use (`legal/`), published copy linked from in-app Settings
- [x] In-app medical disclaimer (Settings screen)
- [x] Play Store listing copy (`store/listing.md`) and Data Safety answers (`store/DATA_SAFETY.md`)
      — ready if you decide to publish there later
- [x] Real release keystore generated and verified: `assembleRelease` produces an APK signed
      with it (not the debug cert), and it launches and runs correctly standalone on the emulator

## Still worth doing

1. **Fill in placeholders** — `[support email]` and `[effective date]` in `legal/PRIVACY_POLICY.md`
   and `legal/TERMS_OF_USE.md` (and the published artifact). Even for a small private group, real
   contact info matters if anyone's trusting the app with health data.
2. **Real-device testing beyond the emulator** — the alarm (including `BootReceiver`'s reboot
   recovery) has only been verified on the emulator. Specifically worth checking on a real
   device: does it still ring after being backgrounded for hours (aggressive battery optimization
   on Samsung/Xiaomi/OnePlus can kill it), and does the on-device AI assistant actually work
   (needs a real AICore-capable device — not available in an emulator).
3. **App icon** — still the placeholder launcher icon, not the Thyme mascot branding.
4. *(Optional)* The release APK is a universal build (~110MB, all ABIs) for maximum compatibility.
   If download size matters for sharing, it can be restricted to `arm64-v8a` (covers virtually
   all real phones from the last several years) for a much smaller file.

## If you do want the Play Store later

1. Create a Google Play Console account ($25 one-time).
2. `store/listing.md` has the listing copy and `store/DATA_SAFETY.md` has drafted answers for the
   Data Safety form and the Alarms & Reminders permission declaration.
3. Upload screenshots + an app icon/feature graphic, complete the content rating questionnaire,
   set the Privacy Policy URL.
4. Play builds need an AAB, not an APK — `./gradlew bundleRelease` instead of `assembleRelease`
   (same signing setup already handles this).
5. Play Store review is typically a few hours to a few days; medical-adjacent apps can take
   longer or get follow-up questions.

## Explicitly out of scope for v1 (by your instruction)

- iOS build/testing
- Cloud sync / account system / data backup
