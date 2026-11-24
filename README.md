# VibeCodeGram

VibeCodeGram is an independent social messenger: its Android app talks only to the VibeCodeGram backend. It does **not** connect to Telegram and does not require Telegram API ID or API Hash.

## Architecture

- Android: Kotlin, Jetpack Compose, Material 3.
- Backend: Go 1.23, Chi HTTP router, PostgreSQL, JWT sessions, bcrypt password hashing.
- Realtime: authenticated WebSocket endpoint.
- Deployment: Docker Compose; the operator deploys the stack on their own VPS.

Telegram Android and exteraGram are feature-research references only. Instagram is a product-pattern reference only. This repository does not copy their source code, private APIs, trademarks, or proprietary assets. No KentGram code, modules, resources, or design are used.

## Backend quick start

1. Copy `.env.example` to `.env`.
2. Set a long random `JWT_SECRET` and change the PostgreSQL password.
3. Run `docker compose up -d --build`.
4. Check `curl http://YOUR_HOST:8080/healthz`.

The API listens on port 8080 inside the compose network. PostgreSQL is not published to the host by default. Put a TLS reverse proxy (for example Caddy or Nginx) in front of the API before exposing it publicly. Keep `.env` private.

### API v1

- `POST /v1/auth/register` — create account (`username`, `password`, optional `display_name`).
- `POST /v1/auth/login` — issue bearer token.
- `GET /v1/me` — current account.
- `GET /v1/conversations` — list memberships.
- `POST /v1/conversations` — create a direct or group conversation.
- `GET /v1/conversations/{id}/messages?limit=50&before=ID` — message history.
- `POST /v1/conversations/{id}/messages` — send a text message.
- `GET /v1/ws?token=JWT` — authenticated realtime stream. Use TLS in production; query tokens may be visible in proxy logs, so configure log redaction.

All conversation and message queries are scoped to the authenticated user. Passwords are stored as bcrypt hashes, never plaintext.

## Android

- Application ID: `com.vibecodegram`
- Minimum SDK 26; compile/target SDK 35.
- Debug builds use `com.vibecodegram.debug` so they can coexist with a release install.

The Android shell is being connected to the API in subsequent increments. A green backend health check does not mean every social or messaging screen is already implemented.

## CI and releases

GitHub Actions builds a debug APK. A distributable release requires one persistent signing keystore and credentials stored as repository secrets. Never rotate the signing key between updates; increment `versionCode` for each release.

## License research

- Telegram Android: GPL-2.0
- exteraGram: GPL-3.0

This project currently uses independently authored implementation code. Any future source reuse requires a separate license and attribution review.
