<p align="center"><img src="logo.svg" width="140" alt="JARVIS logo"></p>

# JARVIS for Android

A free, bilingual (Arabic / English) voice assistant for Android with an arc-reactor HUD.
Phone commands and everyday chat work **offline**; AI answers work **online** through free providers.
It can be set as your phone's default digital assistant. Inspired by Mark LVII by FatihMakes.

## What's new in 0.2
- **Model lists**: pick a model per provider from a list (or leave it on Auto). One tap refreshes the list from your own keys, and dead models are skipped automatically.
- **Offline common sense**: 200 everyday topics with 3 replies each, in Arabic and English (greetings, thanks, feelings, jokes, quick tips).
- **Male assistant voice**: deep "Charon" voice through Gemini (online), with a lower-pitched offline phone voice as fallback.
- **Works on phones without Google services** (Huawei): speech recognition through Groq Whisper.
- **Stable signing key**: new builds install over the old one and keep your settings.

## What works
- Default-assistant integration (corner swipe / assistant gesture where the phone allows it)
- Animated arc-reactor UI showing listening, thinking and speaking
- Offline commands: open apps, flashlight, volume, battery, time, date, timers, alarms, calculator, media controls, play music, YouTube and web search, navigation, dial, email draft
- AI through Gemini, Groq, OpenRouter, Mistral, Cerebras or your own server, with automatic fallback
- API keys stay on the phone

See [FEATURES.md](FEATURES.md) for the roadmap.

## Build the APK (no installs needed)
Push to `main`. GitHub Actions builds the app and publishes `JARVIS.apk` under **Releases**
(also under the run's **Artifacts**). You can also run it by hand from the **Actions** tab.

## Licence
CC BY-NC 4.0, non-commercial, credit FatihMakes. See [NOTICE.md](NOTICE.md).
