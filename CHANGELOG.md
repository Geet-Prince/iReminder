# Changelog

All notable changes to iReminder are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Nothing planned yet. Ideas and bug reports are welcome in
[GitHub Issues](https://github.com/Geet-Prince/iReminder/issues).

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

[Unreleased]: https://github.com/Geet-Prince/iReminder/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/Geet-Prince/iReminder/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Geet-Prince/iReminder/releases/tag/v1.0.0
