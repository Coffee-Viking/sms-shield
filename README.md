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
