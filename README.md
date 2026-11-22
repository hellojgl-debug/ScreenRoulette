# ScreenRoulette

ScreenRoulette is an Android app for discovering films, TV series, and documentaries using personalized filters and random recommendations.

## Install on Android

Download `ScreenRoulette.apk` from [GitHub Releases](https://github.com/hellojgl-debug/ScreenRoulette/releases). Open the APK on an Android 8.0 or later device and follow the install prompt. Android may ask you to allow installation from the browser or file manager you used to open it.

The release APK has a different signing key from the development builds. If Android refuses to update a debug install, uninstall the debug build first. Uninstalling deletes the device's saved filters and watchlist.

## Build

Open the project in Android Studio, or run `./gradlew :app:testDebugUnitTest :app:assembleDebug` with the Android SDK installed. Release builds can be made with `./gradlew :app:assembleRelease` and signed with the release keystore. Keep the signing key outside the repository and use the same key for subsequent releases.

The app connects to the ScreenRoulette backend over HTTPS. TMDB and OMDb credentials are held by the backend and aren't required to build the app.

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability is supplied by TMDB and JustWatch. IMDb and Rotten Tomatoes ratings are retrieved through OMDb.
