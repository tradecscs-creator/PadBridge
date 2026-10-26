# Set up PadBridge

## 1. Prepare your phone

You need Android 10+ with root already configured and a working root manager. Root is not a switch in ordinary Android settings, and installing PadBridge does not add it. This guide assumes root is already available.

The phone's kernel must support UHID and allow the helper to access the selected input device. PadBridge reports connection errors in the app; it does not change SELinux settings or install a modified kernel.

## 2. Connect the controller

Open Android Bluetooth settings, put the gamepad into pairing mode, and pair it. Alternatively, use a supported USB connection. Choose the gamepad's Android-compatible mode if it offers multiple modes.

## 3. Allow PadBridge in your root manager

In Magisk 31.1, open **Superuser → Grant Superuser (+)** and enable **PadBridge Root**. This Magisk version requires granting access in advance. In another manager/version, a permission request may appear when PadBridge starts its helper.

Root is a broad permission. Use the release APK you trust and verify the SHA-256 file if needed.

## 4. Start the bridge

Open PadBridge and tap **Connect using root**, read the access explanation, then tap **Start bridge**. After the bridge is ready, open **Controllers → Refresh list**, choose the physical gamepad, and return to Home.

Tap **Start playing**. Open **Test controller** and move both sticks, press each trigger and button, and check the D-pad. When translation is active, the input source is **Xbox Wireless Controller**.

## 5. Open COD Mobile or another controller-enabled game

Keep PadBridge running and open the game. Enable controller support in the game's settings. For CODM, check the controller settings and enter a practice/training match to check gameplay controls. Some menus use the touchscreen even when controller gameplay is enabled.

Adjust sensitivity in the game. If the layout itself is wrong, use a PadBridge profile.

## 6. Adjust a profile

Pause translation before editing. In **Profiles**, choose **Edit**. You can map the main buttons or a single button, choose a preset, adjust the dead zone, change/invert axes, rename the profile, and import/export JSON. Start translation again to apply the saved settings.

## Pause and stop

**Pause translation** releases the source gamepad while keeping the bridge ready. **Help → Fully stop PadBridge** ends the bridge. Start a new connection after a phone restart. The app does not silently start root when opened or after boot.

## Troubleshooting

| Problem | What to do |
|---|---|
| Root connection fails | Check that root already works and the installed PadBridge Root app is allowed in your manager. In Magisk 31.1, grant it through Superuser before retrying. |
| No controller in the list | Check Android pairing/USB mode, open Controllers and refresh. Try another Android-compatible controller mode if available. |
| Wrong buttons or stick directions | Pause, edit the model's profile, choose the correct axes/button mapping, then restart translation. |
| Camera moves with a released stick | Increase the profile's dead zone gradually. |
| Tester works but a game does not respond | Enable the game's native controller support. Check controls in gameplay, and verify that translation is still active. |
| UHID or input-access error | The kernel or device policy may not allow the required input access. Copy the error from diagnostics and include your phone/root-manager details in a compatibility report. |
| Input stopped after a disconnection | Reconnect the gamepad, refresh Controllers, select it and start translation again. |

Use **Help → View diagnostics** for details. Before sharing a report, remove any private information. Include exact steps and the game/controller modes in your GitHub issue.
