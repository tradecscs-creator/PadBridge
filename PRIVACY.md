# Privacy: free root beta

PadBridge's free root beta stores controller profiles, settings and diagnostic information locally on your Android device. Device information used for setup includes controller names/capabilities, Android model/version and bridge status. The selected controller's input is translated into a local virtual HID gamepad.

The distributed free APK contains no advertising SDK, analytics or automatic diagnostic-upload service. The app/helper connection stays on the device. The Android INTERNET permission is used for that local connection.

PadBridge does not read the screen, messages or game files. Root is nevertheless a broad system permission; Android does not limit a root grant to gamepad access. Grant it only to a build you trust.

Profile export uses the document location you choose. Diagnostic sharing uses an app you select. Those destinations may have their own privacy practices. Reports can contain phone/controller details, profile names and error messages; review them before making them public. Uninstalling removes app-local settings/profiles; exported files or reports already shared elsewhere remain at their chosen destinations.

The separate source-only AdMob demonstration is a different package, uses Google test IDs, cannot start the root bridge and has different network behavior. This document describes the distributed free root APK.

Questions or reports: [tradecscs-creator/PadBridge issues](https://github.com/tradecscs-creator/PadBridge/issues). Do not include private account details or device serial numbers in public issues.
