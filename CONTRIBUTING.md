# Contributing to iReminder

Thanks for considering a contribution. iReminder is a deliberately small app,
so contributions are most welcome when they keep it small, dependable, and
easy to audit.

## Scope

**In scope**

- Bug fixes
- Compatibility fixes for new Android versions
- Accessibility and localisation improvements
- Documentation and translations
- Small, well-scoped features that do not require privileged system access

**Usually out of scope**

- Anything that would require reading, storing, or transmitting data about
  the user's Apple account beyond what the web view already needs
- Background services, persistent notifications, or battery-draining features
- Analytics, telemetry, advertising, or tracking of any kind
- Third-party SDKs that add permissions or network calls
- Anything that bypasses or automates Apple's authentication

## Ground rules

1. **Do not weaken privacy.** No new network destinations, no new
   permissions, no data collection. If a change needs any of those, it needs
   a very good reason and discussion in an issue first.
2. **Keep it simple.** This app's value is that you can read the whole thing
   in one sitting. Large refactors are rarely welcome.
3. **Java, no new runtime dependencies.** The only runtime dependency is
   AndroidX AppCompat. Please do not add others without discussion.
4. **One concern per pull request.**

## Reporting bugs

Open an issue with:

- iReminder version and Android version
- What you expected, and what happened instead
- Steps to reproduce
- A log excerpt if relevant — `adb logcat` output is often useful

Please redact anything personal from logs and screenshots. Never include your
Apple ID, email, or reminder content.

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Make your change.
3. Verify the app builds: `./gradlew assembleDebug`
4. Confirm on a real device if your change affects the UI or the web view.
5. Update `CHANGELOG.md` under `[Unreleased]`.
6. Open the pull request describing what changed and why.

By contributing you agree that your work is licensed under the
[MIT Licence](LICENSE).

## Code of Conduct

Participation in this project is governed by
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
