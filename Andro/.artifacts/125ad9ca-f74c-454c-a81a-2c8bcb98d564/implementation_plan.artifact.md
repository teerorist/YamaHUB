# Fix Android Resource Linking Error

The build fails because `Theme.Material3.DayNight.NoActionBar` is not found. This theme is part of the Google Material Components library, which is currently missing from the project's dependencies. Additionally, there is a case-sensitivity mismatch between the theme name declared in `AndroidManifest.xml` and the one defined in `themes.xml`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///D:/###Users/teerorist/AndroidStudioProjects/YamaHUB/gradle/libs.versions.toml)
- Add the `material` library version and definition.

#### [MODIFY] [build.gradle.kts (app)](file:///D:/###Users/teerorist/AndroidStudioProjects/YamaHUB/app/build.gradle.kts)
- Add `implementation(libs.material)` to the dependencies block.

### Resources

#### [MODIFY] [themes.xml](file:///D:/###Users/teerorist/AndroidStudioProjects/YamaHUB/app/src/main/res/values/themes.xml)
- Rename `Theme.YamaHub` to `Theme.YamaHUB` to match the usage in `AndroidManifest.xml`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:processDebugResources` to verify that resource linking succeeds.
- Perform a Gradle Sync to ensure dependencies are correctly resolved.

### Manual Verification
- Build the project to confirm the `Android resource linking failed` error is resolved.
