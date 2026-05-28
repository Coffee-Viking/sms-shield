# Publishing Prep

## Current Status

SMS Shield is mostly compatible with FOSS distribution expectations from a
dependency and build-tooling perspective:

- Gradle Android project
- Kotlin/Java source, no native code
- Dependencies from Google Maven and Maven Central
- No Firebase, Play Services, ads, analytics, or remote AI services
- Builds from the command line with `./gradlew assembleRelease`

## Blockers Before Public Distribution

1. Publish the source code.

   Current id: `ski.wischnew.shield`

   Public repository:

   ```text
   https://github.com/Coffee-Viking/sms-shield
   ```

2. Commit and tag a release.

   For current app version `1.36` / version code `136`, tag the release commit
   as:

   ```bash
   git tag v1.36
   ```

   In the fdroiddata build block, use the full commit hash for that release.

3. Add real screenshots.

   Put phone screenshots in:

   ```text
   fastlane/metadata/android/en-US/images/phoneScreenshots/
   ```

4. Prepare optional fdroiddata metadata.

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

## Suggested First Publishing Flow

1. Push the source to `https://github.com/Coffee-Viking/sms-shield`.
2. Create a `v1.36` Git tag.
3. Attach the signed `sms-shield_1.36.apk` to a GitHub Release.
4. Add real screenshots under Fastlane metadata.
5. Submit to IzzyOnDroid, or use the GitHub Release with Obtainium.

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
