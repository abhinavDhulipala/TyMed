# Play Console: Data Safety & permissions declarations

Reference answers for the forms inside Play Console — these aren't filled in automatically,
they're what to select/paste when you get there.

## Data Safety questionnaire

TyMed collects nothing, so this section is short:

- **Does your app collect or share any of the required user data types?** → **No**
  - All data (medications, schedules, intake logs, settings) stays in local on-device storage.
  - If you enable Sentry crash reporting (see `legal/PRIVACY_POLICY.md`), you'd instead answer
    **Yes** and declare "Crash logs" / "Diagnostics" data collected for "App functionality",
    not shared with third parties beyond the crash-reporting processor, not linked to identity.
- **Is all user data encrypted in transit?** → N/A (nothing is transmitted) if no crash
  reporting; **Yes** if Sentry is enabled (it uses TLS).
- **Does your app provide a way for users to request data deletion?** → **Yes** — uninstalling
  the app deletes all local data; there's no server copy to separately delete.

## "Alarms & reminders" permission declaration

Play Console requires a written justification for `USE_EXACT_ALARM`. Suggested text:

> TyMed is a medication reminder app. It uses exact alarms to ring a full-screen, alarm-clock
> style reminder at the precise time a dose is scheduled, and to re-ring at a user-configurable
> follow-up interval if the dose hasn't been marked taken. This is core to the app's purpose —
> approximate/inexact scheduling would defeat the point of a medication reminder.

## "Use full-screen intent" declaration (if prompted)

> Used to show the dose-reminder alarm screen even when the device is locked, matching standard
> alarm-clock app behavior, so a scheduled medication reminder is not missed.

## Target audience / content rating

- Not directed at children; select the appropriate "adults" / general audience tier and answer
  the content questionnaire per `store/listing.md`.

## Store listing privacy policy field

Paste the published Privacy Policy URL: `https://claude.ai/artifact/3dDgZ3qApmgKxCY8vDHzLS`
(or your own hosted copy of `legal/PRIVACY_POLICY.md`, once you have one).
