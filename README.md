# VibeCodeGram

VibeCodeGram is an independent Android application. It is not a KentGram fork and does not reuse KentGram code, assets, modules, or design.

## Product direction

- Telegram-inspired messaging capabilities, implemented as an independent client.
- Power-user customization and extensibility informed by publicly documented exteraGram feature concepts.
- Instagram-inspired social surfaces: profiles, media feed, stories, reactions, and discovery.
- A native Android translucent "Liquid Glass" visual language, adapted to Android accessibility and performance.

The named products are references for feature research only. No Instagram source code, private APIs, trademarks, or proprietary assets are included.

## Android project

- Application ID: `com.vibecodegram`
- Kotlin + Jetpack Compose
- Minimum SDK: 26
- Compile/target SDK: 35

## Build

The GitHub Actions workflow builds a debug APK on pushes and pull requests. A distributable release requires a persistent user-owned signing keystore and credentials configured as repository secrets. Never generate a new signing key for every release.

## Security and API credentials

Use VibeCodeGram's own Telegram API ID/hash and any service credentials. Do not commit secrets, production tokens, signing keys, or private configuration files.

## Source references and licensing

Feature research references:
- https://github.com/TelegramOrg/Telegram-Android
- https://github.com/exteraSquad/exteraGram

This initial project scaffold contains independently authored code and does not copy source files from those repositories. Their licenses differ (Telegram Android: GPL-2.0; exteraGram: GPL-3.0). Any future source-code reuse must be reviewed file-by-file, with compatible licensing, notices, and corresponding-source obligations satisfied before distribution.
