# Changelog

All notable changes to iReminder are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing planned yet. Ideas and bug reports are welcome in
[GitHub Issues](https://github.com/Geet-Prince/iReminder/issues).

---

## [1.2.0] — 2026-09-30

Optional local reminder notifications. Reminders keep working exactly as
before; nothing changes unless you switch the feature on.

### Added

- **Optional local notifications for scheduled reminders.** iReminder reads the
  due dates, times, titles, list names, and completion state from the Reminders
  page that is already loaded, and converts each due time into a local Android
  alarm. When the alarm fires, a normal Android notification is shown. The
  notification survives the app being closed, because the alarm is held by
  Android's `AlarmManager` rather than by the app.
- **Notification settings panel**, reachable from a bell button in the bottom
  corner: an on/off switch, the timestamp of the last successful sync, and a
  **Sync now** action.
- **Alarms are restored after a reboot**, after an app update, and after a
  clock or time zone change, all of which can clear Android's alarm schedule.
- **Deterministic, idempotent scheduling.** Each reminder maps to a stable
  alarm id, and a sync only registers alarms for reminders that are new or whose
  time actually changed. Re-syncing unchanged reminders registers nothing new,
  so an existing alarm is never pushed later by a sync.
- **Notification channel** "Reminder notifications" at high importance, so a
  reminder is visible when it is due. The channel can be tuned in system
  settings.
- **Staged diagnostic logging** around every sync attempt, reporting the exact
  failure reason: `PAGE_NOT_LOADED`, `SESSION_NOT_SIGNED_IN`,
  `SESSION_EXPIRED`, `DOM_STRUCTURE_CHANGED`, `ZERO_REMINDERS`,
  `JS_EXECUTION_FAILED`, or `STILL_LOADING`.

### Changed

- Notification and exact-alarm permissions are now requested **in context**,
  when you switch notifications on, instead of at first launch. Exact alarms
  are optional: if declined, Android delivers reminders at a time of its own
  choosing rather than failing.
- At most the 200 soonest reminders are given alarms, so a very large list
  cannot exhaust Android's per-app alarm limit.
- A failed sync now keeps the previous schedule and the previous "last synced"
  timestamp, and reports why it failed rather than clearing existing alarms.

### Fixed

- Reminders render well after `onPageFinished`, because iCloud Reminders is a
  single-page app inside a nested frame. The first parse now reports that the
  page is still loading and is retried on a backoff, instead of declaring a
  structural failure and giving up.
- Due times are no longer off by the current second. A reminder with no explicit
  time is now normalised to the start of its minute, so repeated syncs recognise
  an unchanged reminder as unchanged. Previously every sync cancelled and
  re-registered every alarm, drifting each one by a second.

### Notes and limitations

- These notifications are **generated locally by Android**. They are not Apple's
  official notifications for Android and they are not real-time.
- iReminder keeps no background service and no live WebView, so it can only see
  reminders while the page is loaded. A reminder added or changed on another
  device is not picked up until you open iReminder or tap **Sync now**, and a
  reminder whose time has already passed will not alert you.
- Reminder data is read from Apple's web interface rather than an official API,
  so a change to that page may stop the schedule from updating. iReminder
  reports this rather than guessing, and leaves existing alarms in place.
- Full details, including what is stored locally, are in
  [PRIVACY.md](PRIVACY.md) and the README's Local Notifications section.

---

## [1.1.0] — 2026-09-30

### Fixed

- **The app no longer crashes when you change the theme.** The toggle
  recreated the activity twice: `setDefaultNightMode()` already recreates it
  internally, and the handler then called `recreate()` a second time on an
  Activity that had already been destroyed. That second teardown destroyed the
  WebView while its renderer process was still running, so the renderer took a
  `SIGSEGV` and Crashpad escalated the failure into a full app crash. The
  redundant `recreate()` call has been removed.
  - In practice the first tap usually survived, and the crash reliably hit from
    the second tap onwards, in both the light-to-dark and dark-to-light
    directions.
- A WebView renderer crash is now contained instead of being fatal.
  `onRenderProcessGone()` rebuilds the WebView and reloads the page, so a
  renderer failure costs you a page reload rather than the whole app.

### Changed

- The application ID is now `me.geetprince.ireminders`, replacing the
  `com.example.ireminders` placeholder used in v1.0.0.

### Upgrade notes

- **This release cannot be installed over v1.0.0.** The application ID changed,
  so Android treats it as a different app and refuses to update in place.
  Uninstall v1.0.0 first, then install this release. You will need to sign in to
  iCloud again, as the session was stored under the old app ID.
- Requires Android 7.0 (API 24) or newer, as before.

---

## [1.0.0] — 2026-09-30

First public release.

### Added

- Full-screen Android app that loads the official Apple Reminders web
  interface (`https://www.icloud.com/reminders`) in a native `WebView`, with
  no browser address bar or navigation UI.
- **Persistent Apple session.** Session cookies and web storage are kept in
  the app's private storage, so you sign in once instead of on every launch.
  The session is reused for as long as Apple keeps it valid.
- **Light, dark, and follow-system themes**, with a sun/moon toggle button in
  the bottom corner and a long-press to return to following the system
  setting. The chosen theme is remembered across restarts.
- **Edge-to-edge layout** with a transparent status bar, and automatic status
  bar icon contrast so the clock and system icons stay readable in both
  themes.
- **Correct back-button behaviour** — back navigates within the app's history
  and only exits the app at the start of history.
- **External links open in your browser.** Links outside Apple domains are
  handed to your normal browser instead of opening inside the app.
- Automatic WebView state restoration across configuration changes and app
  restarts.
- Original iReminder app icon (adaptive, with round and legacy variants).

### Security and privacy

- Sign-in happens only on Apple's own sign-in page. iReminder has no login
  form, never handles your Apple ID or password, and does not bypass or
  automate Apple's authentication or two-factor prompts.
- No advertising, analytics, or tracking of any kind.
- No iReminder account and no third-party account required.
- Only two permissions requested: `INTERNET` (to load Apple's site) and
  `ACCESS_NETWORK_STATE` (to check connectivity). No dangerous permissions.
- Documented in [PRIVACY.md](PRIVACY.md).

### Notes

- iReminder is an independent open-source project and is **not** affiliated
  with, endorsed by, or sponsored by Apple Inc. Apple and Apple Reminders
  are trademarks of Apple Inc.
- Released under the [MIT Licence](LICENSE).
- Requires Android 7.0 (API 24) or newer.

[Unreleased]: https://github.com/Geet-Prince/iReminder/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/Geet-Prince/iReminder/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/Geet-Prince/iReminder/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Geet-Prince/iReminder/releases/tag/v1.0.0
