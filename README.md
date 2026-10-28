# FocusMark

**Make time for what matters.** FocusMark is an Android app that helps you limit distracting apps, YouTube Shorts and Instagram Reels. Its focus rules and activity counts run locally, including when offline.

## What it does

- Choose apps to block and set a daily focus schedule.
- Choose Shorts and Reels blockers and see daily activity counts.
- After feed-recognition consent, the combined daily allowance is 100 recognized Shorts and Reels. Counting continues during breaks; hiding the optional floating counter does not stop it.
- Request a break: protection stays active for 20 minutes, followed by a 10-minute break. Cancel the wait or resume early at any time.
- Works offline, with light and dark themes.

## Getting started

FocusMark requires Android 10 or later. Install a trusted APK, open FocusMark, select what to block and the schedule, then connect its Accessibility service when prompted. Read the in-app disclosure before granting access.

## Privacy and limits

The Accessibility service observes foreground apps and, after consent, visible labels needed to identify supported feeds and browser routes. Rules, deadlines and counts are stored locally. The Android app does not send usage data to the included API scaffold. Recognition can fail as Instagram, YouTube, browsers or Android change. Device owners can disable Accessibility, force-stop or uninstall the app; OEM battery restrictions can also interrupt it. This is a personal focus tool, not tamper-proof device management. See [Privacy](PRIVACY.md).

## Build from source

Install Android Studio, JDK 17 and Android SDK Platform 36. Open `android/` in Android Studio, let Gradle sync, and run the app on an Android 10+ device. On Windows, run `cd android` and `gradlew.bat assembleDebug` to build a development APK. Debug builds use a local development certificate. Release builds are unsigned until a distributor configures signing. A build signed with a different key cannot update an existing installation.

`backend/` is an unauthenticated development-only FastAPI scaffold. It is not used for focus enforcement and must not be exposed to the internet.

## Status and contributing

Current source: 0.3.1. See [verification notes](VERIFICATION-v0.3.1.md) for prior checks and their limits. Instagram hardware coverage remains incomplete. Contributions and issue reports are welcome; see [Contributing](CONTRIBUTING.md) and [Security](SECURITY.md).

## License

Available under the [MIT license](LICENSE). Third-party components retain their own licenses.
