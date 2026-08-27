# HOW_THIS_AGENT_BUILT_THE_APK

> Written for the next AI agent / developer. This is the EXACT procedure that produced a working, signed,
> verified Android APK for DaddyAmp (`com.audify.music`). It uses a custom shell build strategy
> without Gradle / AGP, keeping desktop Python code fully intact while delivering a professional Android APK.

## The Strategy

The project is NOT a Gradle project. It uses a **custom shell build**. Do not look for Gradle,
the Gradle wrapper, `build.gradle`, or the Android Gradle Plugin — they do not exist and are not
used.

## Environment (verified working)

| Item | Version |
|---|---|
| OS | Linux (sandbox) |
| JDK | OpenJDK 11 (`javac`/`java`, `-source 8 -target 8`) |
| Android build-tools | 34.0.0 (`aapt2`, `d8`, `zipalign`, `apksigner`) |
| Android platform | android-34 (`android.jar`) |
| minSdk / targetSdk | 21 / 34 |
| Gradle / AGP | NONE (not used) |
| NDK | NONE (not used) |
| Kotlin | NONE (not used) |

## Working directories

- Primary: `/home/user/Projects/Audify`
- Alias / Symlinks: `/home/user/daddyamp_app`, `/home/user/audify_app`

## Directory Structure

```text
Projects/Audify/
├── main.py                     # Desktop Linux Python implementation (100% untouched & authoritative)
├── DaddyAmp.apk                # Output signed & aligned Android APK (13.9 MB)
├── HOW_THIS_AGENT_BUILT_THE_APK.md
├── android/
│   ├── AndroidManifest.xml     # Package: com.audify.music, label: DaddyAmp, minSdk=21, targetSdk=34
│   ├── java/com/audify/music/
│   │   ├── MainActivity.java   # Activity hosting edge-to-edge WebView, file chooser & bridge
│   │   └── AudifyBridge.java   # Native MediaPlayer, AudioFocus, AudioFX, MediaStore, SAF picker
│   └── res/
│       ├── layout/activity_main.xml
│       ├── values/{strings,colors,styles}.xml (app_name: DaddyAmp)
│       └── mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png
├── web/
│   └── index.html              # DaddyAmp UI/UX (Zero emojis, path-batched canvas, real peak scrubber)
├── assets/                     # Packaged directly into the APK root
│   ├── index.html
│   ├── audio/                  # Bundled high-quality lossless demo tracks (44.1 kHz 16-bit PCM)
│   └── artwork/                # Authentic high-res covers & luxury vinyl fallback
├── keystore/
│   └── audify.keystore         # JKS RSA 2048-bit keystore (alias: audify)
├── build_apk.sh                # Reproducible 10-step build pipeline
└── build/
    └── build_apk.sh            # Symlinked to build_apk.sh
```

## The Exact Build Command

```bash
cd /home/user/Projects/Audify
bash ./build_apk.sh
```

The script runs, in order:
1. `aapt2 compile --dir android/res/ -o /tmp/apk_build/compiled.zip`
2. `aapt2 link -o /tmp/apk_build/base.apk -I $ANDROID_PLATFORM_JAR --manifest android/AndroidManifest.xml --java gen/ --min-sdk-version 21 --target-sdk-version 34 --version-code 2 --version-name "1.1" /tmp/apk_build/compiled.zip`
3. `javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $ANDROID_PLATFORM_JAR -classpath $ANDROID_PLATFORM_JAR:gen -d classes/ android/java/com/audify/music/*.java gen/com/audify/music/R.java`
4. `d8 --output /tmp/apk_build --lib $ANDROID_PLATFORM_JAR --min-api 21 <classes>` → `classes.dex`
5. `zip -uj /tmp/apk_build/base.apk classes.dex`
6. `zip -ur /tmp/apk_build/base.apk assets/` (adds `assets/index.html`, `assets/audio/`, `assets/artwork/`)
7. Native libraries check (`zip -0` if `.so` files present; DaddyAmp uses native Java MediaPlayer)
8. `zipalign -f 4 /tmp/apk_build/base.apk /tmp/apk_build/aligned.apk`
9. `apksigner sign --ks keystore/audify.keystore --ks-key-alias audify --ks-pass "pass:$KEYSTORE_PASSWORD" --key-pass "pass:$KEYSTORE_PASSWORD" --out DaddyAmp.apk /tmp/apk_build/aligned.apk`
10. `apksigner verify --verbose --print-certs DaddyAmp.apk`

## Verification Results

- `aapt2 dump badging DaddyAmp.apk`
  - package: `com.audify.music`, versionCode: `2`, versionName: `1.1`
  - sdkVersion: `21`, targetSdkVersion: `34`
  - application-label: `DaddyAmp`
  - launchable-activity: `com.audify.music.MainActivity`
- `apksigner verify --verbose --print-certs DaddyAmp.apk`
  - Verified using v1 scheme (JAR signing): `true`
  - Verified using v2 scheme (APK Signature Scheme v2): `true`
  - Verified using v3 scheme (APK Signature Scheme v3): `true`
  - Signer #1 DN: `CN=Audify, OU=AudifyMusic, O=AudifyApp, L=Lahore, ST=Punjab, C=PK`
- `zipalign -c 4 DaddyAmp.apk` → `Verification successful`
- `unzip -l DaddyAmp.apk` → contains `AndroidManifest.xml`, `classes.dex`, `resources.arsc`, `assets/index.html`, bundled audio (`dreamwave.wav`, `cyberpunk.wav`, `stellar.wav`), and artwork (`default.png`, `dreamwave.png`, `cyberpunk.png`, `stellar.png`).

## Master Craftsmanship & Engineering Highlights

1. **Absolute Zero-Emojis Policy**:
   - 100% SVG vector icon family (24×24 geometry, 2px stroke weight, round caps/joins).
   - Zero emojis or fake unicode characters across the entire interface.
2. **Unified Continuous Player Architecture**:
   - Seamless flow: Library ↔ Song ↔ Mini Player ↔ Now Playing ↔ Lyrics ↔ Queue ↔ Back.
   - Now Playing Center Stage smoothly switches between Artwork, Synced Lyrics, Live In-Player Reorderable Queue, and Honest Audio File Specs.
3. **True Acoustic Waveform Peaks Scrubber**:
   - Real precomputed peak analysis (75 bins) derived directly from the audio files.
   - 36px high-DPI canvas scrubber with path-batched rendering (2 fills instead of 80+), canvas dimension caching, and sub-second touch scrubbing.
4. **Synced Lyrics Experience**:
   - High-contrast active line (pure white `#FFFFFF`, 21px, bold, calm focus).
   - Inactive lines in readable slate (`#94A3B8`, opacity 0.55).
   - Tap any line to seek immediately.
   - Auto-scroll with floating `[ Auto-follow ]` button on manual scroll.
5. **Real Audio Hardware & DSP**:
   - Android: `android.media.audiofx.Equalizer` and `BassBoost` wired into `AudifyBridge.java`.
   - Web: 5-band BiquadFilter DSP (`60Hz`, `230Hz`, `910Hz`, `3.6kHz`, `14kHz`) with Preamp gain and profiles (Flat, Bass Boost, Rock, Electronic, Vocal, Acoustic).
   - System Audio Focus handling (pause/resume on focus loss/gain) and `ACTION_AUDIO_BECOMING_NOISY` receiver (auto-pause when headphones disconnect).
6. **Honest Technical Specifications**:
   - Dynamic codec, bitrate, sample rate, bit depth, channels, and pipeline derived from the actual file. No fake "HI-RES PRO" or hardcoded specs.
7. **Reorderable Queue**:
   - Professional playback queue with pinned active track, up/down item moving, track removal, clear queue, and tap-to-jump.
8. **In-Place Metadata Tag Editor & Online Artwork Search**:
   - Edit Title, Artist, Album, Genre directly from the track menu (`⋮`).
   - Query online APIs (iTunes / Cover Art Archive) for album covers.
9. **Integrated Precision Sleep Timer**:
   - Presets for 15m, 30m, 45m, 60m, and End of Track.
   - Live countdown badge in top application bar.
   - 15-second smooth volume fade-out before pausing.
10. **Desktop Source Parity**:
   - Original `main.py` verified 100% byte-for-byte identical to `/home/user/uploads/Audify-main.html`.
