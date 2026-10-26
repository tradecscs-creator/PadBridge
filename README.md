# PadBridge

**Your controller. More possibilities.**

PadBridge is an **Xbox controller emulator for compatible Android gamepads**. On a rooted Android phone, it reads your Bluetooth or USB gamepad and creates a **local virtual Xbox controller** for **Call of Duty: Mobile (CODM)** and other games with native controller input.

**Native controller input — no touch mapping.** The game receives sticks, triggers, buttons and D-pad events from the virtual Xbox controller. PadBridge does not convert buttons into screen taps or require a touch-control overlay.

Use the controller you already own, adjust its layout, and keep a separate profile for each model.

**Free beta · English interface · Root required**

[Download the beta](https://github.com/tradecscs-creator/PadBridge/releases/tag/v0.5.3-root-beta) · [Setup guide](docs/SETUP.md) · [Report your setup](https://github.com/tradecscs-creator/PadBridge/issues/new?template=compatibility.yml)

## What it does

- Translates both sticks, both triggers, ten main buttons and the D-pad into Xbox-style controller input.
- Lets you remap buttons, select or invert axes, and adjust stick dead zones.
- Saves controller profiles locally, with JSON import and export.
- Includes a live controller tester and connection diagnostics.
- Provides pause and stop controls that release the physical gamepad.
- Runs directly on the phone after root access is configured; no PC or debugging activation is needed during use.

PadBridge is designed for compatible dual-stick gamepads. Profiles adapt their button and axis layouts to Xbox-style input. Compatibility depends on the phone, controller mode and game.

## What you need

| Requirement | Details |
|---|---|
| Android phone | Android 10 or newer |
| Existing root access | A working root manager and `su`, such as Magisk. PadBridge does not install root. |
| Compatible kernel | UHID support and permission for the root helper to read the selected controller |
| Controller | A dual-stick Bluetooth or USB gamepad paired/connected to Android |
| Game | Native controller support enabled in the game's settings |

Root gives broad system access. Grant it only to the PadBridge APK you trust; use the signed download in this repository's Releases.

## Quick start

1. Install the APK from [Releases](https://github.com/tradecscs-creator/PadBridge/releases).
2. Pair your controller in Android's Bluetooth settings, or connect it over USB.
3. Allow PadBridge Root in your root manager. **Magisk 31.1:** open **Superuser → Grant Superuser (+) → PadBridge Root** and grant access beforehand. Other managers/versions may ask when you start the bridge.
4. In PadBridge, tap **Connect using root → Start bridge**.
5. Open **Controllers**, refresh the list if needed, and select your gamepad.
6. Tap **Start playing**, then open **Test controller** to check sticks, triggers and buttons. The translated input is named **Xbox Wireless Controller**.
7. Open your game and enable its controller support.

Use **Pause translation** between sessions, or **Help → Fully stop PadBridge** to end the bridge.

## Call of Duty: Mobile (CODM)

Start translation in PadBridge before opening CODM. In the game's controller settings, enable controller support, then enter a practice or training match to check movement, camera, triggers and buttons. Menu navigation may still use the touchscreen.

If a button or axis behaves differently, pause translation, edit your controller profile, and start translation again. See the [step-by-step setup and troubleshooting guide](docs/SETUP.md).

## Help improve controller support

[Share your setup](https://github.com/tradecscs-creator/PadBridge/issues/new?template=compatibility.yml): phone model, Android version, root manager, controller model/mode, game version and what works or needs adjustment. Clear feedback helps improve the next beta.

Do not post serial numbers, private identifiers or root activation tokens. Review diagnostic reports before sharing them.

## Privacy and ads

The free root beta has **no advertising SDK or analytics**. Profiles are stored on your phone; profile export and diagnostic sharing happen only when you choose them. The bridge communicates with its helper locally on the device. Read [Privacy](PRIVACY.md).

The source also contains a separate Google test-banner demo. It cannot start the root bridge and is not the APK distributed in this beta.

## Build from source

JDK 17, Android SDK Platform 36, Build Tools 36.0.0 and the included Gradle 8.13 wrapper:

```sh
bash ./gradlew testFreeDebugUnitTest assembleFreeDebug assembleFreeRelease bundleFreeRelease lintFreeDebug lintFreeRelease
```

On Windows, use `gradlew.bat`. Release outputs are unsigned by default; keep signing keys outside the repository. [Developer notes](docs/DEVELOPMENT.md).

PadBridge is an independent project and is not affiliated with Microsoft or Activision. Xbox and Call of Duty names belong to their respective owners.
