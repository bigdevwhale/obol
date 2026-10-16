<div align="center">

# Obol

### Every crossing of Lethe costs an obol

*Time with books, languages, lessons and meditations earns obols. Reels, Shorts and TikTok cost them: Charon waits at the gate of every endless feed. No ads, no account, no internet access.*

[![Android CI](https://github.com/bigdevwhale/obol/actions/workflows/android.yml/badge.svg)](https://github.com/bigdevwhale/obol/actions/workflows/android.yml)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-14100E?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Languages](https://img.shields.io/badge/lang-EN%20%7C%20RU-D9774A)
[![License: MIT](https://img.shields.io/badge/license-MIT-DDB45E)](LICENSE)

</div>

## The idea

In the Greek myth, Lethe is the river of forgetting, and Charon ferries the dead across for an obol. An endless feed is the same river: you drink, and an hour is gone. Obol makes every crossing cost something, and pays you for the time you spend on what matters.

| | | |
|---|---|---|
| 🏛️ | **Pantheon** | Patrons of useful time: Calliope (reading), Hermes (languages), Athena (learning), Hestia (meditation), Nike (sport). Create your own with any name, symbol, glaze, rate, daily cap, apps and sites. |
| 🌊 | **Lethe** | Streams that cost obols: Instagram, TikTok, YouTube Shorts, VK, Reddit, X, or anything you add. "Only the feed" makes Reels and Shorts paid while messages stay free. |
| ⛵ | **Charon's gate** | Opening a stream brings up the gate: why do you want to cross, a few breaths, then 5 / 15 / 30 minutes, the meter, or a free pass from Hermes' staff. Turning back is always free. |
| 🌙 | **Tariffs** | Nyx's hour (night ×3), early morning ×2, and a rising fare for every crossing after the first one today. |
| ⚖️ | **Debt to Hades** | An empty purse can go below zero, with interest on the borrowed part. |
| 🕯️ | **Rites** | Time away from the phone (a paper book, practice, a workout) by a timer that only runs while the phone lies face down. |
| 📜 | **Ledger** | A potsherd for every day and a four-week frieze of service and forgetting. |

## How it works

Charon is an accessibility service. It reads which app is in front, the address bar of common browsers, and the view ids that give away Reels or Shorts. Nothing leaves the phone: the app has no internet permission.

Obol is installed from the APK (it is not on Google Play).

## Build

```sh
./gradlew assembleDebug          # app.obol.dev
./gradlew assembleDebug -Pdemo   # app.obol.demo with four weeks of made-up history
./gradlew testDebugUnitTest lintDebug
```

Part of [Stillcraft](https://bigdevwhale.github.io/stillcraft/).
