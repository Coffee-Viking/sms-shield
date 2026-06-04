# SMS Shield

SMS Shield is an Android SMS app focused on local, on-device message filtering.

It can be used as the default SMS app and supports:

- Keyword, sender, number, wildcard, and country-based block and allow rules
- Unicode message text, including non-ASCII SMS content
- Blocked message suppression and a dedicated blocked folder
- Manual block overrides for false positives
- Message and archive filters for all, received, and sent SMS
- Archive and skip-archive controls
- Chat grouping with optional split-by-inactivity behavior
- Dual-SIM send options and SIM indicators, including old SIM/eSIM labels
- OTP copy support from notifications and message view
- Local import and export of app data via JSON file
- Light, dark, and AMOLED themes with selectable accent colors

The app does not use any web services or data connectivity at all. No cloud
spam detection, no advertising SDKs, no analytics SDKs, no Firebase, no Google
Play Services, no AI services. Pure rule-based on-device handling.

## Build

```bash
./gradlew assembleRelease
```

For local signed release builds, provide a private `keystore.properties` file.
Do not commit release keys or generated APKs.

## Installation

As long as the app is not listed in any major app store, you may run into warning
messages while installing. When installing from the release APK, you may need to
pause Google Play Protect temporarily.

Step-by-step:

- Open the Google Play Store app.
- Tap the profile icon in the top right corner.
- Scroll to "Play Protect" and tap.
- Tap the settings icon in the top right corner.
- Deactivate "Scan apps with Play Protect".
- When asked to confirm, select "Pause".

Afterwards, SMS Shield should install normally and you can reactivate Play Protect.

Subsequent updates will install without disabling Play Protect, but you may need
to approve the installation with your fingerprint or system PIN: In the installation 
dialogue, select "more details -> install anyway".

When opening the app for the first time, Android 13 and newer may refuse SMS-related
access at this point due to the use of functions with elevated access (reading SMS), and
subsequently fail to set as default SMS app, which is however needed to access the message
storage.

To manually set SMS Shield as the default SMS app:

- Navigate to Settings -> Apps -> App management.
- Find SMS Shield.
- Tap the three-dot menu in the top right corner and activate "Allow restricted settings".
- Go to Settings -> Default apps -> SMS app.
- Select SMS Shield.

Both aspects will be resolved once this app gets listed in app stores, this issue only
exists temporarily while sideloading from GitHub or other archives.

## Antivirus False Positives

A small number of antivirus engines on VirusTotal currently flag the release APK.
I believe these are false positives related to SMS Shield being a default SMS app
with SMS permissions and OTP copy functionality. Malicious apps can abuse similar
permissions and OTP access to exfiltrate codes or hijack accounts, which is likely
why some engines classify the APK as "Trojan", "Generic.Spy", or "Generic.Riskware".

I have contacted the affected antivirus vendors and requested manual review. Some
have already acknowledged the request and indicated that the flag will be removed.
Related correspondence is tracked in `false positives/`.

## License

SMS Shield is licensed under the GNU General Public License version 3.0 only.
See `LICENSE`.
