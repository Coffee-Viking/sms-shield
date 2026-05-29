# SMS Shield

SMS Shield is an Android SMS app focused on local, on-device message filtering.

It can be used as the default SMS app and supports:

- Keyword, sender, number, wildcard, and country-based block and allow rules
- Unicode message text, including non-ASCII SMS content
- Blocked message suppression and a dedicated blocked folder
- Archive and skip-archive (freeze to inbox) controls
- Dual-SIM send options and SIM indicators (including old SIM/eSIM)
- OTP copy support from notification & from message
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

As long as the app is not listed in any major app store (work in progress), you may
run into warning messages while installing. As a current workaround when installing
from the release .apk, you need to pause Google Play Protect.

Step-by-Step:

- Open the Google Play Store app.
- Tap on the profile icon in teh top right corner
- Tap on the "Play Protect" button
- Click the settings (cog) icon in the top right corner
- Deactivate the setting "Scan Apps with Play Protect"
- When asked to confirm, select "Pause"

Afterwards, SMS Shield will install fine and you can reactivate Play Protect.

## License

SMS Shield is licensed under the GNU General Public License version 3.0 only.
See `LICENSE`.
