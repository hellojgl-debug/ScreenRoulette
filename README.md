# ScreenRoulette

ScreenRoulette is an Android app for discovering films, TV series, and documentaries using personalized filters and random recommendations.

Open a title’s details to watch its YouTube trailer in a compact player. Turn your phone sideways to expand it while it plays. Swipe right to go back through screens you’ve visited, or left to go forward. Films open newest-first within your filters. Spin samples matching releases throughout the selected years, and the filter panel lets you choose one to five titles per spin. The default is three.

## Licence

ScreenRoulette is licensed under the PolyForm Noncommercial License 1.0.0.

Personal, educational and other permitted non-commercial use and modification are allowed.

Commercial use requires separate permission from the relevant copyright holders.

See [LICENSE.md](LICENSE.md) and [COMMERCIAL.md](COMMERCIAL.md) for details.

## Install on Android

Download `ScreenRoulette.apk` from [GitHub Releases](https://github.com/hellojgl-debug/ScreenRoulette/releases). Open the APK on an Android 8.0 or later device and follow the install prompt. Android may ask you to allow installation from the browser or file manager you used to open it.

The release APK has a different signing key from the development builds. If Android refuses to update a debug install, uninstall the debug build first. Uninstalling deletes the device's saved filters and watchlist.

## Build

Open the project in Android Studio, or run `./gradlew :app:testDebugUnitTest :app:assembleDebug` with the Android SDK installed. Release builds can be made with `./gradlew :app:assembleRelease` and signed with the release keystore. Keep the signing key outside the repository and use the same key for subsequent releases.

The app connects to the ScreenRoulette backend over HTTPS. TMDB and OMDb credentials are held by the backend and aren't required to build the app.

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability is supplied by TMDB and JustWatch. IMDb and Rotten Tomatoes ratings are retrieved through OMDb.
