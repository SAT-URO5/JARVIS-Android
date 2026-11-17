<p align="center"><img src="logo.svg" width="140" alt="JARVIS logo"></p>

# JARVIS for Android

A free, bilingual (Arabic / English) voice assistant for Android with an arc-reactor HUD.
It works **offline** for phone commands and **online** through free AI providers, and it can be set
as your phone's default digital assistant. Inspired by Mark LVII by FatihMakes.

## What works in v0.1
- Default-assistant integration (corner swipe / assistant gesture where the phone allows it)
- Animated arc-reactor UI that shows listening, thinking and speaking states
- Offline commands: open any app, flashlight, volume, battery, time, date, timers, alarms, calculator,
  media controls, play music (Spotify or a chooser), YouTube and web search, navigation, dial, email draft
- AI through Gemini, Groq, OpenRouter, Mistral, Cerebras or your own server, with automatic fallback
- Speech in and out using the phone's own engines (on-device when available), Arabic and English
- API keys stay on the phone

See [FEATURES.md](FEATURES.md) for the roadmap of 235 planned abilities.

## Get the APK without installing anything
1. Create a GitHub repo and upload this project (drag the folder contents into the web page).
2. Open the **Actions** tab, then **Build APK**, then **Run workflow**.
3. When it finishes (about 5 minutes), download **JARVIS-apk** from the run page, unzip it, install `JARVIS.apk`.

## Licence
CC BY-NC 4.0, non-commercial, credit FatihMakes. See [NOTICE.md](NOTICE.md).
