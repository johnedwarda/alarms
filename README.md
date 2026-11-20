# Alarms

An Android alarm clock where every alarm sits on one week grid: days across the top, hours down the side. Built from the "Alarm App Reimagined" design (Dusk style).

- **Tap** an empty spot to add an alarm; **press and hold, then drag** to pick the exact time.
- **Dots**: a ring rings once, a single dot is weekly, a row of dots repeats (daily, weekdays or chosen days).
- **Edit**: drag a selected dot to change the time, tap dots to pick days, ± to fine-tune, rename, Once / Weekly / Daily / Weekdays.
- **Skip, don't switch off**: skip the next ring or public holidays (Finland); the alarm turns itself back on afterwards.
- **Ringing**: full-screen over the lock screen, one-tap snooze, swipe to dismiss. Tones are synthesized on device (Sunrise fades in gently).
- Dusk and Light themes, 12/24-hour clock, vibration, undo for discarded/deleted alarms.

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```sh
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # alarm timing tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: 8.0 (API 26).

## Licenses

The Outfit typeface is bundled under the SIL Open Font License 1.1, see [licenses/Outfit-OFL.txt](licenses/Outfit-OFL.txt).
