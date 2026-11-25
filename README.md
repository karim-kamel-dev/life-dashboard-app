# Life Dashboard

A private, offline Android app to log your day and see your own patterns.

- Fixed checklist of 8 starter activities per day: green (done), red (not done), or unanswered, with a notes box and photos for each. The 8 activities are a starting list; adding or editing activities is not built yet.
- A 24-hour grid with free text per hour.
- A year grid and frequency bars for 7 days, a month and a year.
- Money from fields you fill in yourself. A missing entry shows "Not recorded", never 0. The balance is a computed balance, not your real balance.
- Photo library kept inside the app. Add photos from the Today tab with the "+ Photo" chip on an activity; the Photos tab is the library. In the printed or HTML review sheet you can choose which photos to include, photo by photo.
- Text, HTML and print/PDF review sheet you can share yourself. Nothing is sent anywhere automatically. The app has no internet permission and no accounts.
- Zip backup and restore.
- Themes: olive glass, graphite metal, night violet, sunset. Dark, light or system mode, and a custom accent color. Green and red stay fixed because they carry meaning.
- Languages: Arabic (Egyptian, default), English, Spanish, French, German, Portuguese, Russian, Chinese (Simplified). Right-to-left layout for Arabic. The translations were written by an AI assistant and have not been reviewed by native speakers. Activity names are translated too; they are not user-defined yet. Pull requests that improve a translation are welcome (see `Strings.kt`).

Insights are frequencies of what you recorded. They say "probable pattern", never a proven cause. Days you did not log and rest days are not failure.

## Install

Download the APK from the Releases page and open it on an Android phone (Android 8.0 or newer). Android will ask you to allow installing from your browser or file manager. The APK is signed with a key kept in this repository's CI secrets, and is not from Google Play.

## Build

Android SDK 34, JDK 17, Gradle 8.7. `gradle :app:assembleDebug`. Unit tests: `gradle :app:testDebugUnitTest`. Emulator tests: `gradle :app:connectedDebugAndroidTest`.

## Status

Early preview, version 0.1. Automated tests run on an Android emulator in CI. It has had only a first trial on a real phone.

## License

MIT, see LICENSE.
