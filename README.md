# NotiFilter

Lightweight Android notification filter. OTP first, AI spam check, regex mode.

## How it works (pass-through model)

NotiFilter never sends notifications from its own app. Important notifications
pass through untouched and appear exactly as the original app's notification
(same app name and icon). Spam is cancelled and kept in the Blocked list only.

1. **OTP first (regex)** — if a notification matches the OTP pattern, it is
   never cancelled or held. It shows instantly, and a copy is saved in the
   Inbox with a Copy button.
2. **AI check** — everything else is sent to OpenRouter or Google Gemini
   (your own API key, set in the AI tab). The AI says spam or not, with a
   score and a reason. Spam is cancelled; the rest is left alone.
3. **Regex mode** — when enabled, the AI is skipped entirely. Decisions come
   only from your editable Block keywords, Always-allow list and OTP regex.
4. **Image analysis** — notifications with an image are checked with OCR +
   a vision model (promo banners, QR codes) when enabled in Rules.

While the AI decides, a non-OTP notification is briefly snoozed, then it
either reappears untouched (not spam) or is cancelled (spam).

## Screens

- **Inbox** — search, All / OTP / Important filters, notification history.
- **Blocked** — spam with the reason it was caught (AI score or keyword),
  Restore / Always allow / Delete.
- **Rules** — AI / Regex mode, OTP regex, Block and Always-allow keywords,
  image-analysis toggles.
- **AI** — OpenRouter and Google (Gemini) keys, model pickers, Test
  connection, spam strictness.
- **Analytics** — only from the header 3-dot menu: overall totals plus a
  per-app breakdown (total, real, spam, spam % / real %) computed from the
  stored history, so the numbers always match the Inbox and Blocked lists.

## Permissions

- **Notification access** — required, so the app can read and cancel
  notifications. Granted in system Settings (the app opens that screen).
- **Notifications (Android 13+)** — only for the app's own internal alerts,
  never to re-post another app's notifications.

API keys are stored on the device only (SharedPreferences). They are never
logged, never committed, and never leave the device except in the API call
you configured.

## Build

GitHub Actions builds the debug APK on every push (see
`.github/workflows/build.yml`, Java 17 + Gradle). The APK is uploaded as
the `NotiFilter-debug-apk` artifact.

- Package: `com.nakib.notifilter`
- minSdk 26, targetSdk 34, Kotlin + XML Views, Material 3
