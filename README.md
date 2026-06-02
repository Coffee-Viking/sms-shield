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
- Tap "Play Protect".
- Tap the settings icon in the top right corner.
- Deactivate "Scan apps with Play Protect".
- When asked to confirm, select "Pause".

Afterwards, SMS Shield should install normally and you can reactivate Play Protect.

## License

SMS Shield is licensed under the GNU General Public License version 3.0 only.
See `LICENSE`.
