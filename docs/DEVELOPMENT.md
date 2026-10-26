# Development

JDK 17; Android SDK Platform 36 and Build Tools 36.0.0; AGP 8.13.2; Gradle 8.13. Minimum Android API: 29.

`freeDebug` uses package dev.padbridge.app.beta. `freeRelease` uses dev.padbridge.app. The free flavor has no ad SDK and permits user-initiated root startup. Release outputs are unsigned by default.

`admobDebug` is a separate Google sample-banner demonstration (dev.padbridge.app.admobdemo); ROOT_START_ALLOWED is false in both the UI and service. `admobRelease` is disabled. Do not insert production advertising identifiers into the free root beta.

The APK starts its bundled helper through su only after an explicit action. The helper verifies UID 0, uses authenticated loopback IPC, reads/grabs one selected evdev controller, and creates a local virtual HID through UHID. Pause/stop/EOF close the device handles. Profiles are validated and stored per model. No externally downloaded helper, screen-touch mapper, auto-root exploit, boot receiver or global permission/policy changes are used.

Run unit tests and lint with the wrapper before submitting changes. CI builds unsigned candidates only. Preserve signing material outside this repository; never commit keys, private configuration, live tokens or diagnostic dumps.

The UI is English. Controller-reported names and user-created profile names retain their original text.
