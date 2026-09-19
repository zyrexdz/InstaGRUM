# InstaGRUM

An offline Instagram simulator for Android. Post photos, reels and stories, go live, and watch a fake audience react to it in real time.

There's no account, no server, and no internet permission in the manifest. Everything runs on your phone.

## What it actually does

Most fake-follower apps just make a number go up. This one models how reach works instead.

When you post, it goes out to a slice of your followers plus a slice of strangers through recommendations. Some of them like it. How many like it early decides how many more people see it next. A post that lands keeps spreading, one that doesn't quietly dies off. Post the same thing twice and you'll get different numbers both times.

Stories only reach people who already follow you. Posts and reels can reach anyone. That's why a new account can blow up from a single post but gets nothing on stories.

## Numbers

Sampled from actual runs, not targets written into the code:

| Followers | Post after 3 min (Viral) | Live peak (Natural) |
|---:|---:|---:|
| 1K | ~1.7K views | ~25 watching |
| 100K | ~11K views | ~2K watching |
| 1M | ~96K views | ~18K watching |
| 10M | ~946K views, ~104K likes | ~120K watching |

## Features

**Growth**
- 9 pace settings, from Quiet up to Celebrity
- Early likes and comments feed back into how far a post travels
- Poisson arrivals, day/night cycle, random quiet and busy stretches
- Switch pace mid-run and everything you've already posted adapts

**Live**
- Viewer count rises, dips and settles instead of climbing forever
- Hype button pulls in a surge of viewers — tap again to turn it off and let the room settle back down
- Chat scales with room size and reacts to your stream title
- Gaining followers while streaming raises the room live

**Comments**
- Reads your caption, location and media type, then picks from 21 topics
- Beach photo tagged Lisbon gets comments about the water and the city
- ~1.9 million unique usernames, thousands of comment variations
- Keyword matching, not an AI model, so it's instant and costs no battery

**Stories**
- Tap for a photo, hold to record video up to 60 seconds
- Gone after 24 hours unless you save them to a highlight
- Impressions, reactions, replies, taps forward and back, profile visits
- Ring goes grey once you've seen everything, lights up again when you post

**Accounts and data**
- Multiple profiles, switch from the username menu
- Each account keeps its own posts, followers and settings
- Atomic writes with a rotating backup, so a crash mid-save can't corrupt anything
- Included in Android backup, plus manual export/import to a JSON file
- Optional foreground service keeps growth running while the app is closed

## Install

Grab `app-release.apk` from [Releases](https://github.com/zyrexdz/InstaGRUM/releases) and open it on your phone. You'll need to allow installs from unknown sources when it asks.

Needs Android 8.0 or newer.

## Building it yourself

### 1. Install JDK 21

Gradle 8.11 and the Android plugin don't work on JDK 25 yet, so you want 21 specifically.

- **Windows:** `winget install Microsoft.OpenJDK.21`
- **macOS:** `brew install openjdk@21`
- **Linux:** `sudo apt install openjdk-21-jdk`

Check it worked:

```bash
java -version
```

If that prints something other than 21, point Gradle at the right one:

```bash
# macOS / Linux
export JAVA_HOME=/path/to/jdk-21

# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21"
```

### 2. Install the Android SDK

If you have Android Studio, you already have it and can skip this.

Otherwise download the [command line tools](https://developer.android.com/studio#command-tools), unzip them, and run:

```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
sdkmanager --licenses
```

### 3. Point the project at your SDK

Make a file called `local.properties` in the project root:

```properties
# Windows
sdk.dir=C:/Users/YourName/AppData/Local/Android/Sdk

# macOS
sdk.dir=/Users/YourName/Library/Android/sdk

# Linux
sdk.dir=/home/YourName/Android/Sdk
```

Use forward slashes on Windows too. Backslashes need escaping and it's easy to get wrong, which gives you a confusing `volume label syntax is incorrect` error.

This file is gitignored because the path is different on every machine.

### 4. Build

```bash
git clone https://github.com/zyrexdz/InstaGRUM.git
cd InstaGRUM

# Linux / macOS only, first time
chmod +x gradlew

./gradlew assembleDebug
```

APK lands in `app/build/outputs/apk/debug/`. On Windows use `gradlew.bat` if `./gradlew` doesn't work.

Install it with `adb install -r app/build/outputs/apk/debug/app-debug.apk`, or just copy the file to your phone.

### Release builds

`./gradlew assembleRelease` works out of the box and falls back to the debug key.

If you want your own signing key so you can ship updates without uninstalling:

```bash
keytool -genkeypair -v -keystore instagrum-release.jks \
  -keyalg RSA -keysize 4096 -validity 10950 -alias instagrum
```

Then create `keystore.properties`:

```properties
storePassword=whatever_you_chose
keyAlias=instagrum
keyPassword=whatever_you_chose
```

Both files are gitignored. **Keep the `.jks` somewhere safe** — lose it and you can't update an installed app, only uninstall and reinstall.

### Tests

```bash
./gradlew testDebugUnitTest
```

38 tests covering the growth model, persistence and UI flows.

## Troubleshooting

**`Unsupported class file major version`** — you're on the wrong JDK. Set `JAVA_HOME` to 21.

**`SDK location not found`** — missing `local.properties`, see step 3.

**`volume label syntax is incorrect`** — your `sdk.dir` path is malformed. Use forward slashes.

**`Permission denied` running gradlew** — `chmod +x gradlew`.

**`App not installed` on your phone** — the installed copy was signed with a different key. Uninstall it first, then install again.

**Build hangs on Windows** — a stale daemon. Run `./gradlew --stop` and try again.

## Tech

Kotlin, Jetpack Compose, Material 3, CameraX, Media3, kotlinx.serialization, WorkManager.

Single activity, unidirectional data flow. Every state change goes through a pure reducer, which keeps the simulation deterministic and testable. The engine is seeded, so identical inputs replay to identical results.

```
model/       state and actions
data/        reducer, persistence, runtime
simulation/  growth engine, livestreams, comment generation
ui/          Compose screens
```

## What it doesn't do

It won't predict anything. The model is tuned to feel believable, not to forecast what a real account would actually get.

Comments aren't AI-generated. They're keyword matched against your caption and can't see what's in your photos.

Background growth has limits. Android kills foreground services under aggressive battery settings, especially on Samsung. The app asks for a battery exemption, and totals get reconciled when you reopen it either way.

## Disclaimer

Not affiliated with Instagram or Meta. Doesn't connect to Instagram, can't post anywhere, has no access to real accounts. Every person and comment in it is made up and generated on your device.

## License

MIT

---

Built by [zyrexdz](https://github.com/zyrexdz)
