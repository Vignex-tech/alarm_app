# Alarm Clock

A small, dependency-free native Android alarm app (Java, minSdk 26).

- Multiple alarms with optional label and repeat days (or one-time)
- Rings reliably via `AlarmManager.setAlarmClock` (exact, works in Doze)
- Full-screen ringing screen over the lock screen, with Snooze (10 min) and Dismiss
- Alarms survive reboot, app updates, and time/timezone changes

## Get the APK

Every push to `main` builds the app in GitHub Actions and publishes `AlarmClock.apk` on the
[Releases](../../releases) page. On your phone: open the latest release, download
`AlarmClock.apk`, and install it (allow "Install unknown apps" for your browser if asked).
On first launch, allow notifications.

## Build locally

Needs JDK 17 and the Android SDK (platform 34):

```
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

## Signing note

`app/release.jks` is a throwaway key committed so that every build is signed the same way
and installs as an update over the previous one. It is fine for a personal sideloaded app;
generate your own and move it to CI secrets before publishing anywhere.
