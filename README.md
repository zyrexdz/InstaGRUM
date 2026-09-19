# InstaGRUM

A fully offline Instagram simulator for Android. Post photos, reels and stories, go live, and watch a believable audience react in real time — with no account, no network, and no data leaving your phone.

The app ships with **zero internet permission**. There is nothing to sign up for and nothing to pay for.

---

## Why it exists

Most "fake follower" apps just count upward. A number goes up, a progress bar fills, and none of it feels like anything.

InstaGRUM models the thing that actually makes social media feel alive: **distribution**. A post is shown to a slice of your followers and a slice of strangers. Some of them like it. That early response decides how many more people see it. Good posts snowball, weak ones fade, and the same upload twice never gives the same result.

Everything you see is generated on-device from a seeded simulation. No hardcoded numbers, no scripted outcomes.

---

## Features

### Growth that behaves like a real feed

- **Nine pace presets** from Quiet to Celebrity, each a different distribution environment rather than a multiplier on a counter
- **Momentum** — measured likes and comments feed back into how far a post travels
- **Separate channels** — posts and reels reach non-followers through recommendations; stories stay inside your follower network
- **Human timing** — Poisson arrivals, day/night cycles, and random quiet and busy phases
- Change your pace mid-run and every existing post, story and livestream adapts immediately

### Scale, without fake ceilings

| Followers | Post, first 3 min (Viral) | Live peak (Natural) |
|---:|---:|---:|
| 1K | ~1.7K views | ~30 viewers |
| 100K | ~11K views | ~3.5K viewers |
| 1M | ~96K views | ~50K viewers |
| 10M | ~946K views · ~104K likes | ~300K viewers |

Numbers above are sampled from the simulation, not targets baked into the code. Run it twice and you will get different figures.

### Livestreams

- Viewer counts that rise, dip and settle instead of climbing forever
- **Hype button** — trigger a surge and pull in a wave of new viewers
- Chat that scales with room size, with comments that react to your stream title
- Live viewer count responds to follower growth while you are still streaming

### Comments that fit the post

Keyword topic matching reads your caption, location and media type, then picks from topic-specific reactions across 21 categories — food, travel, gym, pets, fashion, music and more. Post a beach photo tagged Lisbon and people mention the water and the place.

No model file, no network call, no lag. Roughly 1.9 million distinct usernames and thousands of comment variations, so a viral post does not repeat itself.

### Stories

- Tap the shutter for a photo, hold to record video up to 60 seconds
- Expire after 24 hours unless saved to a highlight
- Full insights: impressions, reactions, replies, navigation taps, profile visits
- Ring turns grey once you have viewed everything, and lights up again when you post

### Multiple accounts

Switch between separate profiles from the username menu. Each account keeps its own posts, followers, settings and history in an isolated snapshot.

### Your data stays yours

- Atomic writes with a rotating backup, so an interrupted save cannot corrupt a profile
- Included in Android's backup, so a reinstall or a new phone restores everything
- Manual export and restore to a JSON file you control
- Accounts are only deleted when you explicitly delete them

### Keeps running when closed

An optional foreground service advances every account continuously. Leave it off and a periodic worker still reconciles elapsed time, so totals stay correct either way.

---

## Install

Download the latest `app-release.apk` from [Releases](https://github.com/zyrexdz/InstaGRUM/releases) and open it on your phone. You may need to allow installs from unknown sources.

Requires Android 8.0 (API 26) or newer.

---

## Build from source

```bash
git clone https://github.com/zyrexdz/InstaGRUM.git
cd InstaGRUM
./gradlew assembleRelease
```

Output lands in `app/build/outputs/apk/release/`.

Requires JDK 21. The release build is signed with a local keystore that is intentionally not committed — generate your own with `keytool` and point `signingConfigs` at it, or build the debug variant instead.

---

## Tech

Kotlin · Jetpack Compose · Material 3 · CameraX · Media3 · kotlinx.serialization · WorkManager

Single-activity, unidirectional data flow. All state changes run through a pure reducer, which keeps the simulation deterministic and testable. The engine is seeded, so the same inputs always replay to the same outcome.

```
model/       immutable state and actions
data/        reducer, persistence, runtime
simulation/  growth engine, livestreams, comment generation
ui/          Compose screens
```

38 unit tests cover the growth model, persistence and UI flows.

---

## Honest limitations

- **Not a predictor.** The model is calibrated to feel right, not to forecast what a real account would do.
- **Comments are not AI-generated.** They use keyword matching against your caption, not a language model, and cannot see image contents.
- **Background execution has limits.** Android will stop a foreground service on aggressive battery settings. The app prompts for an exemption, but totals reconcile on reopen regardless.

---

## Disclaimer

InstaGRUM is not affiliated with, endorsed by, or connected to Instagram or Meta. It does not connect to Instagram, cannot post anywhere, and has no access to real accounts. Every person, comment and interaction is fictional and generated on your device.

Built for anyone curious about how reach and engagement actually compound.

---

## License

MIT

---

Built by [zyrexdz](https://github.com/zyrexdz)
