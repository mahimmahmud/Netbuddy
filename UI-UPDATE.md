The reference-inspired UI was introduced in 1.1. In version 1.3.1, the Home screen is a network dashboard with local/public IP, ISP, approximate IP location and Wi-Fi signal status. Diagnostic tool cards are on the Tools tab. Public IP and metadata use independent HTTPS providers with fallbacks; see README.md for the privacy note.

# NetBuddy 1.1 — UI update

The Android Compose implementation has been redesigned using the supplied UI concept as its visual reference. The concept's example IP addresses, results, timestamps and certificate details are not hardcoded into the app.

## Changes

- Compact horizontal NetBuddy brand header with a transparent shield mark and settings button.
- Connection card with transport icon, Android validation status and IPv4 address.
- Responsive two-column tool cards (one column on very narrow screens or large font scale).
- Latest report card; TCP quality explanation moved into a tappable Details dialog.
- Teal accents, white/light-gray surfaces, navy dark backgrounds, rounded panels and consistent vector line icons.
- Proper navigation icons replace the previous text symbols.
- TCP input panel, attempt rows, error details, min/average/max summary, status badge and copy action.
- DNS/HTTPS input and result panels using real diagnostic state.
- History filter chips and report cards with type icons, timestamps, summaries and delete controls.
- Subnet result rows, copy/save actions, styled Guide cards and About/Appearance panels.
- Android system-bar icon appearance follows the app theme.
- Version code 2; version name 1.1-ui. Existing Room schema, database filename and diagnostic algorithms are unchanged.

## Use

Extract this complete project into a new folder. Open `NetBuddy` in Android Studio. There is no need to copy individual files into the old project.

For a quick device check, install `APK/NetBuddy-debug.apk`. If its signing certificate matches the installed version, an update can preserve the app's local reports. A differently signed installation cannot be updated directly; uninstalling deletes that installation's local data.

Home gear icon -> Appearance -> Light reproduces the light direction of the reference. Dark uses navy surfaces. System follows Android's theme. The app uses one consistent selected theme across its screens; the reference image juxtaposes light and dark designs.

## Verification limits

See VALIDATION.md for build and unit-test results. Device visual checks and live networking checks have not been performed in this environment. Font scale, display size and Android system bars can affect exact spacing.

## Source changes

`MainActivity.kt`, `UiKit.kt`, `ui/subnet/SubnetScreen.kt`, `app/build.gradle.kts`, `res/drawable/brandmark.xml` and the `res/drawable/ui_*.xml` vector resources. Complete updated source is included.
