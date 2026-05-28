# Publishing Prep

## Current Status

SMS Shield is mostly compatible with FOSS distribution expectations from a
dependency and build-tooling perspective:

- Gradle Android project
- Kotlin/Java source, no native code
- Dependencies from Google Maven and Maven Central
- No Firebase, Play Services, ads, analytics, or remote AI services
- Builds from the command line with `./gradlew assembleRelease`

## IzzyOnDroid Status

Ready:

- Public source repository:

  ```text
  https://github.com/Coffee-Viking/sms-shield
  ```

- Permanent app id:

  ```text
  ski.wischnew.shield
  ```

- GPL-3.0-only license
- Fastlane metadata: short description, full description, icon, changelog
- Git tag:

  ```text
  v1.37
  ```

- GitHub Release with signed APK:

  ```text
  https://github.com/Coffee-Viking/sms-shield/releases/tag/v1.37
  ```

Still needed:

- Add real screenshots.

  Put phone screenshots in:

  ```text
  fastlane/metadata/android/en-US/images/phoneScreenshots/
  ```

- Open an app inclusion request at IzzyOnDroid's Codeberg issue tracker.

  IzzyOnDroid asks requesters to read the inclusion criteria and then request
  inclusion at their issue tracker:

  ```text
  https://izzyondroid.org/contact/
  ```

  A ready-to-paste issue body is in `IZZYONDROID_REQUEST.md`.

## Optional Main F-Droid Status

Main F-Droid is not the current target, but metadata is parked here for later.

Prepare optional fdroiddata metadata:

   Copy `fdroid/metadata.template.yml` into the `fdroiddata` fork as:

   ```text
   metadata/<application-id>.yml
   ```

## Suggested Public Repo Contents

Commit:

- `app/`
- `gradle/`
- `fastlane/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradlew`
- `gradlew.bat`
- `README.md`
- `LICENSE`

Do not commit:

- `keystore.properties`
- `app/*.keystore`
- `local.properties`
- `release/`
- `backups/`
- `mockup/`
- `.gradle/`
- `app/build/`

## Optional Main F-Droid Flow

1. Fork `https://gitlab.com/fdroid/fdroiddata`.
2. Create `metadata/<application-id>.yml`.
3. Run, if `fdroidserver` is available:

   ```bash
   fdroid rewritemeta <application-id>
   fdroid checkupdates --allow-dirty <application-id>
   fdroid lint <application-id>
   fdroid build <application-id>
   ```

4. Commit with:

   ```bash
   git commit -m "New App: <application-id>"
   ```

5. Open a merge request against `fdroid/fdroiddata`.
