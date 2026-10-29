# Sony Bravia IR Remote

Modern infrared remote Android APK for **Sony Bravia TV Model 32EX310**.

## Download

Latest release (signed APK):

[sony-bravia-ir-remote.apk](https://github.com/abraar05/sony-bravia-ir-remote/releases/latest/download/sony-bravia-ir-remote.apk)

## Features

- Material Design dark UI
- Power, Volume, Channel, Mute, Input
- Home / Back / Menu / Display / Sound
- Netflix & YouTube smart-TV buttons
- Number pad 0-9
- Sony SIRC protocol (40 kHz carrier)
- Vibration feedback on tap
- Works on any phone with an IR blaster (Android 5.0+)

## Install

1. Download the APK from the link above.
2. On your phone, enable "Install unknown apps" for your browser/file manager.
3. Open the APK and install.
4. Point the top edge of your phone at the TV and tap a button.

## IR Codes

Buttons use Sony SIRC 15-bit codes (TV device address = 1). Codes can vary
by region/panel. Edit `src/com/sonybravia/irremote/MainActivity.java`
`CODES` table and rebuild if a button does not respond.

## Build

Requires Android SDK build-tools 34.0.0:

```bash
bash build.sh
# writes build/sony-bravia-ir-remote.apk
```

No Gradle, no third-party libraries.
