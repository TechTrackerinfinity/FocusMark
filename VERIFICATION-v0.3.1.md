# FocusMark v0.3.1 verification

VersionName `0.3.1`, versionCode `14`, application ID `com.focuslock.app`.

APK: `<local APK output>\FocusMark-v0.3.1-release.apk`.

SHA-256: `919DB64070A9A1C23E035178A942EEBC5E0492D00723ADEA2E3EA3934357FFA9`.

## Change

Shorts/Reels totals are mandatory after feed-recognition consent. The Activity screen has a read-only Automatic counting card instead of per-platform stop switches. Both flags are derived as enabled whenever allowance tracking is enrolled; legacy disable calls are rejected. Feed records update platform totals and the combined quota even if an older saved counting flag is false. No daily totals, quota or pause deadlines are reset during this update.

Show floating counter controls overlay visibility only. Hiding the badge cannot stop service monitoring, either platform total, or the 100-item allowance. A disconnected service shows a warning that enforcement is unavailable and provides a reconnect action.

## Checks

- All **87 tests passed**, zero failures/errors. Includes new tests for rejecting counter-disable calls, recording both totals with a hidden badge, old disabled flags, preserved quota/pause deadlines, enrolling both counters together and the disconnected-service UI warning.
- Timer, schedule-lock, daily quota, browser matching, feed deduplication, counter animation and UI regression tests remain passing.
- `testDebugUnitTest`, `lintRelease` and `assembleRelease` passed; lint has 25 non-blocking warnings and zero errors.
- APK signature verified and matches the previously installed app's local signing identity.
- Installed successfully on the connected Poco F6; package manager reports v0.3.1/code 14 without DEBUGGABLE. Accessibility remains bound; no crashed services reported.
- **User confirmed on the phone:** after turning Show floating counter OFF, watching three different Shorts and returning to Activity, both Shorts today and the combined daily allowance increased.
- All **13 prior APKs** were checked by SHA-256 and remain unchanged. The new release is added to the requested APK folder.

## Limits

Counting has no in-app stop/reset path. Android still allows the device owner to revoke Accessibility, force-stop, clear data or uninstall. OEM service interruptions and target-app layout changes can also affect detection. This consumer app cannot prevent those owner/system-level actions; stronger guarantees need separately provisioned device management. Instagram hardware verification is still pending because Instagram is not installed on this phone.

The v0.3.0 phone check also confirmed that requesting a pause displays the countdown and locks schedule/blocking choices. The exact 20-minute wait and 10-minute break boundaries are covered by persisted policy tests.
