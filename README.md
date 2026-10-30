# TV OS launcher

Custom Android TV launcher plus a remote cursor for mobile APKs.

- Shows every launchable app, including mobile-only APKs.
- Favourites row, hide apps, app info, uninstall (long press OK on an app).
- Cursor service: mobile apps get a pointer and a cyan highlight box on whatever is under it.
  D-pad moves it, OK taps, long OK long-presses, pushing against a screen edge scrolls.
- Cursor mode: AUTO (only for non-TV apps), ON, OFF. Change it with the button on the home screen,
  or press MENU / RED on the remote.

## Build
Push to GitHub. Actions builds the APK. Download it from the run's Artifacts (tvos-apk).

## Install
    ./scripts/setup.sh TV_IP:5555 app-debug.apk

Android 11+ wireless debugging: run `adb pair IP:PAIRPORT` first, then `adb connect IP:PORT`.

## Debloat
    ./scripts/debloat.sh TV_IP:5555 list
    ./scripts/debloat.sh TV_IP:5555 remove remove.txt

Never remove: Play Services, Play Store, System UI, Settings, Package Installer.

## Back to stock launcher
    adb shell pm uninstall com.vibe.tvos
