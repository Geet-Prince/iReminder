# Security Policy

## Supported versions

iReminder is a small app maintained as a solo open-source project. Only the
latest release receives fixes.

| Version | Supported |
|---|---|
| v1.0.0 (latest) | ✅ |
| Older versions | ❌ |

## Reporting a vulnerability

**Please do not open a public issue for a security problem.**

GitHub provides private vulnerability reporting for this repository. Use
**Security → Report a vulnerability** on the
[Security tab of this repository](https://github.com/Geet-Prince/iReminder/security).

If that option is unavailable, open a public issue that describes the problem
**without** any working exploit details, and ask for a private channel.

Please include:

- What the issue is and what an attacker could achieve
- Steps to reproduce, or a proof of concept
- The iReminder version and your Android version
- Whether the issue requires physical access to the device

### What to expect

- Acknowledgement within a few days
- An assessment of severity and impact
- A fix and a new release if the issue is confirmed, credited to you unless you
  prefer otherwise

Please give reasonable time for a fix before disclosing publicly.

## Scope

iReminder is a web client. Most of what it displays is served by Apple over
HTTPS using the platform's own `WebView`, so the large majority of its
attack surface is Android's and Apple's, not this project's.

**In scope** — for example:

- Data being sent somewhere other than Apple or the device
- Leaking session data, cookies, or local storage to another app
- Insecure local storage of credentials
- A bug that grants access to data the app should not reach
- Code that intercepts or modifies what you type into Apple's pages

**Out of scope** — for example:

- Vulnerabilities in Android itself or in the `WebView` component
- Vulnerabilities in Apple's iCloud web interface, sign-in, or services
- Attacks requiring physical access to an already-unlocked device
- Users being tricked into installing malware (iReminder is distributed as a
  signed APK on the Releases page — verify the signature if unsure)
- Denial of service against Apple's servers
- Missing hardening with no demonstrated impact

## Verifying the APK

The release APK is signed. To confirm you downloaded an authentic build:

1. Download `iReminder-v1.0.0.apk` from the
   [Releases page](https://github.com/Geet-Prince/iReminder/releases).
2. Compare its SHA-256 checksum against the one published in the release
   notes.
3. Optionally verify the signer certificate fingerprint.

## Security properties of the current release

For transparency, v1.0.0:

- Communicates with Apple's domains over HTTPS only.
- Stores session cookies in the app-private data directory, which other apps
  cannot read, and which is removed on uninstall.
- Requests no dangerous permissions.
- Contains no advertising, analytics, or tracking code.
- Contains no native code and no third-party runtime dependencies other than
  AndroidX AppCompat (Apache 2.0).
- Contains no obfuscated or dynamically downloaded code; what ships in the
  APK is what the public source builds.

See [PRIVACY.md](PRIVACY.md) for the full privacy picture.
