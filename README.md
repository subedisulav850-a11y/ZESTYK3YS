# Zesty AES Extractor

Simple script-style AES / IV extractor for Free Fire Advance (and any game).

## Defaults
- Name: FF Advance
- Package: `com.dts.freefireadv`

## How to use
1. Install APK
2. Magisk / KernelSU → Superuser → allow **Zesty AES**
3. Open app → tap **FORCE ROOT**
4. Status must show `Root: GRANTED ✓`
5. Tap **START EXTRACTOR**
6. Open the game → Login screen
7. Keys appear in app + `/sdcard/Download/zesty_keys.txt`

## Optional better capture
Push matching frida-server:
```
adb push frida-server /data/local/tmp/
adb shell su -c "chmod 755 /data/local/tmp/frida-server"
```
Then restart extractor.
