# Lifetime

A private, offline-first Android app for capturing photos and videos into one calm,
continuous timeline of your life. No accounts, no cloud: everything stays on the phone.

## Install

Every push builds a debug APK on GitHub Actions. Open the repository's
[Releases](../../releases) page on your phone, download `lifetime-debug.apk` from the
newest **Debug build**, and allow "install unknown apps" for your browser when asked.

### Signed builds

The `signed` CI job builds a release APK signed with the keystore in the repository
secrets (keystore as base64, store password, key alias, key password). Builds from `main`
are published as the **Lifetime (signed)** release; other branches publish a signed
pre-release. The signed app (`app.lifetime`) installs alongside the debug app
(`app.lifetime.debug`); they keep separate timelines.

## What's in the MVP

- **Capture**: take a photo or record a short video (up to 60 s) in the app, with
  front/back camera, tap to focus and pinch to zoom.
- **Import**: pick existing photos and videos with the system photo picker. The original
  date is read from EXIF / video metadata so old photos land on the right day.
- **Timeline**: newest first, grouped by year, month (sticky header) and day, with a
  date picker to jump anywhere. Videos show a play badge and their length.
- **Viewer**: full-screen and swipeable; photos pinch/double-tap to zoom, videos play
  with Media3.
- **Manage**: details sheet (date, type, size, location with "open in maps"), delete,
  and export everything to a `.zip` (all files plus a `moments.json` index).
- **Location**: optional, off by default, toggled on the camera screen.

## How it is built

Kotlin, Jetpack Compose (Material 3), Room, CameraX, Media3 and Coil. `minSdk 26`,
`targetSdk 35`.

```
app/src/main/java/app/lifetime/
  LifetimeApp.kt, AppContainer.kt   app start-up and a small manual DI container
  data/
    Moment.kt, MomentDao.kt, LifetimeDatabase.kt   Room: one row per photo/video
    MediaStorage.kt          files in app-private storage: media/YYYY/MM/IMG_….jpg
    MediaMetadataReader.kt   capture date, GPS, size, duration from EXIF / MP4
    MomentRepository.kt      capture, import, delete, export (keeps DB and files in step)
    LocationProvider.kt      optional location via the platform LocationManager
    AppPreferences.kt        small settings (location tagging)
  ui/
    timeline/   grouped feed, jump to date, import/export
    camera/     CameraX photo + video capture
    viewer/     pager, zoomable photo, Media3 video, info sheet, delete
    components/ thumbnails, formatting, system bar helpers
    theme/      calm sand/sage palette, dark and light
```

### Storage decisions

Media lives in the app's private storage (`files/media/`), not the shared gallery. That
needs no storage permission, keeps the timeline private, and avoids duplicate entries in
the gallery. The trade-off is that uninstalling the app deletes it, so use
**Export everything** for backups.

## Building locally

Open the project in Android Studio (Ladybug or newer) or run:

```
./gradlew assembleDebug
```
