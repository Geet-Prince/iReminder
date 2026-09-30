# Privacy Policy — iReminder

**Last updated: 2026-09-30**

iReminder is an independent, open-source project. It is not affiliated with,
endorsed by, or sponsored by Apple Inc. Apple and Apple Reminders are
trademarks of Apple Inc.

This policy explains what iReminder does and does not do with your
information. It is written in plain language on purpose.

---

## The short version

- iReminder **never asks for, receives, or stores your Apple ID or password.**
- iReminder has **no servers**. Nothing you do in the app is sent to the
  project author.
- iReminder contains **no advertising**, and no analytics or tracking SDKs.
- iReminder talks to **Apple** and to nothing else.
- Reminder notification data stays **on your device**. Nothing about your
  reminders is sent to the project author or to any third party.

---

## How sign-in works

When you open iReminder, it loads Apple's Reminders web interface inside an
Android `WebView` — the same web page you would see in a mobile browser.

You sign in on **Apple's own sign-in page**, hosted by Apple. Your Apple ID
and password are typed directly into that page, which is served by Apple over
HTTPS. Your credentials are handled by Apple, exactly as they are when you
sign in to iCloud in a browser.

**iReminder is not a login form.** It has no text fields of its own, cannot
read what you type, and has no code that transmits credentials anywhere.

Because sign-in happens on Apple's page, iReminder also cannot bypass, weaken,
or automate Apple's authentication. If Apple asks you to verify your identity
with two-factor authentication, that prompt comes from Apple and must be
completed on Apple's side.

## What is stored on your device

To keep you signed in, Android's `WebView` stores session data locally in
iReminder's own private app storage, including:

- **Cookies** issued by Apple's servers (session and login cookies)
- **Website storage** such as `localStorage` / `sessionStorage` used by the
  iCloud web interface

This data lives in your device's app-private storage. It is **not** readable
by other apps, is not uploaded anywhere by iReminder, and is removed if you
uninstall iReminder or clear the app's data.

This is standard browser behaviour: it is the same kind of storage Chrome or
Safari use to keep you logged in.

**iReminder does not decrypt, inspect, log, or transmit this data.**

## Reminder notifications and what they store locally

If you turn on **Scheduled reminder notifications**, iReminder reads the due
dates, times, titles, list names, and completion state **from the Reminders page
that is already loaded in the app**, in order to decide which Android alarms to
register. That reading happens on your device, against the page you are already
looking at.

To manage those alarms, iReminder keeps a small record in its private app
storage containing, per scheduled reminder:

- a stable reminder reference
- the reminder title
- the due timestamp
- the list name, whether notes exist, the recurrence text, and the first
  attached link

This is **scheduling metadata only**. It is not a copy of your Reminders
library: it holds no reminder body text, no notes content, no attachments, and
no completed history, and it is limited to reminders that actually have an
alarm. It is never transmitted anywhere, and it is deleted when you turn
notifications off or uninstall the app.

Notifications are generated **locally by Android**. They are not delivered by
Apple and are not real-time: a change you make in iCloud on another device is
only picked up the next time you open iReminder and let it read the page.

iReminder does not use Apple's private API, and does not attempt to sign in to
or authenticate against any Apple service other than the public Reminders web
page you already use.

## What iReminder does not collect

iReminder collects **no** personal information and sends **no** data to its
author. Specifically, it does not:

- Collect your name, email address, phone number, or Apple ID
- Collect or store your Apple ID password
- Upload, transmit, or share your reminders, or any part of them
- Track your location, contacts, photos, files, or clipboard
- Gather analytics, telemetry, crash traces, or usage statistics
- Show advertising or ad identifiers
- Require any iReminder account of its own

There is no account to create. There is nothing to sign up for.

## Permissions iReminder requests

| Permission | Why it is needed |
|---|---|
| `INTERNET` | Required to load Apple's Reminders web interface. Without it the app cannot function. |
| `ACCESS_NETWORK_STATE` | Lets the app check whether a network connection is available before loading pages. |
| `POST_NOTIFICATIONS` | Shows the reminder alert. Requested only when you switch notifications on (Android 13+). Declared but unused if you never enable them. |
| `RECEIVE_BOOT_COMPLETED` | Re-registers scheduled reminder alarms after a reboot, because Android clears all alarms on restart. Declared only. |
| `SCHEDULE_EXACT_ALARM` | Lets a reminder alert fire at its exact scheduled time rather than a batched time Android picks. Requested only when you switch notifications on, and only on Android 12+. You can decline; reminders still arrive, just less punctually. |

Only `POST_NOTIFICATIONS` is a runtime permission the user can see and control.
iReminder requests **no** dangerous permissions. It cannot access your camera,
microphone, contacts, location, files, or device sensors.

## Data handled by Apple

Your reminders, your Apple account, and the content shown inside iReminder
are handled by **Apple** under
[Apple's Privacy Policy](https://www.apple.com/legal/privacy/). Those terms —
not this policy — govern that data.

Because iReminder only displays Apple's web interface, using iReminder is
subject to the iCloud terms you already accepted with Apple.

## Third-party components

iReminder is built with the Android SDK and
[AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat)
(Apache License 2.0). These are standard, well-audited libraries. AppCompat
is used for light/dark theme handling and performs no network activity.

No advertising, analytics, or tracking libraries are included.

## Children

iReminder is not directed at children under 13 and is not a social product.
Because it has no data collection at all, it collects no information from
anyone, including children.

## Changes to this policy

If this policy changes, the updated version will be committed to this
repository and the "Last updated" date above will change. Material changes
will also be noted in the [Changelog](CHANGELOG.md).

## Contact

Questions about this policy or the source code are welcome as a
[GitHub issue](https://github.com/Geet-Prince/iReminder/issues).

Because this is an individual open-source project rather than a company, bugs
and security reports are best raised as issues or via GitHub's private
reporting flow described in [SECURITY.md](SECURITY.md).
