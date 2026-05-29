# SMS Shield

SMS Shield is an Android SMS app focused on local, on-device message filtering.

It can be used as the default SMS app and supports:

- Keyword, number, wildcard, and country-based block and allow rules
- Unicode message text, including non-ASCII SMS content
- Blocked message suppression and a dedicated blocked folder
- Archive and skip-archive controls
- Dual-SIM send options and SIM indicators
- OTP copy support
- Local import and export of app data
- Light, dark, and AMOLED themes with selectable accent colors

The app does not use cloud spam detection, advertising SDKs, analytics SDKs,
Firebase, Google Play Services, or external AI services.

## Build

```bash
./gradlew assembleRelease
```

For local signed release builds, provide a private `keystore.properties` file.
Do not commit release keys or generated APKs.

## License

SMS Shield is licensed under the GNU General Public License version 3.0 only.
See `LICENSE`.

## Publishing Status

Publishing prep is tracked in `PUBLISHING.md`.

The current publishing path is GitHub Releases plus IzzyOnDroid. Fastlane
metadata and screenshots are included in this repository.
