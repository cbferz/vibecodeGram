# vibecodeGram

vibecodeGram is an independent social messenger: its Android app talks only to the vibecodeGram backend. It does **not** connect to Telegram and does not require Telegram API ID or API Hash.

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
- `POST /v1/posts/video` — publish an MP4 video post (multipart `body`, `tag`, `video`; max 50 MiB).
- `GET /v1/posts?sort=videos` — list video posts.
- `GET /v1/posts/{id}/video` — authenticated MP4 stream with byte-range support.
- `GET /v1/me/badges` — verified badges for the authenticated account.
- `GET /v1/devices/accounts?device_id=...` — accounts registered on this app installation.
- `POST /v1/devices/accounts` — register the currently authenticated account on a device (`device_id`).
- `DELETE /v1/devices/accounts/{accountID}?device_id=...` — remove a saved account from that installation.
- `POST /v1/admin/users/{id}/badges` — staff badge assignment (`{"badge":"team"}`).
- `DELETE /v1/admin/users/{id}/badges/{badge}` — revoke a staff badge.
- `PUT /internal/subscriptions/{id}` — subscription-provider webhook; requires `X-Subscription-Secret`.
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

## Hugging Face AI backend

The Go API exposes authenticated AI routes under `/v1`:

- `GET /v1/ai/models` — Hugging Face chat model catalogue, filtered by the server allowlist.
- `POST /v1/ai/chat` — assistant chat with a short conversation history.
- `POST /v1/ai/tools/translate` — translation (`target_language`).
- `POST /v1/ai/tools/summarize` — text summary.
- `POST /v1/ai/tools/rewrite` — rewrite while preserving meaning.
- `POST /v1/ai/tools/grammar` — spelling and grammar correction.
- `POST /v1/ai/tools/post` — social post drafting.
- `POST /v1/ai/tools/smart_reply` — three suggested replies (nothing is sent automatically).
- `POST /v1/ai/tools/context_search` — rank passages in supplied context against a query.

All AI routes require the normal user Bearer token. The Hugging Face token stays on the server and must never be placed in the Android app.

Add these values to the server's `.env` (copy from `.env.example`):

```dotenv
HF_TOKEN=hf_your_fine_grained_token
HF_AI_MODELS=openai/gpt-oss-20b:fastest,deepseek-ai/DeepSeek-R1:fastest
```

The token needs permission to make calls to Inference Providers. Configure only models available to that Hugging Face account. Hugging Face performs provider routing for each configured model. The API tries the next configured model only for transient upstream outages (502/503/504); it does not rotate models to bypass quota, billing, 401/403 or 429 limits. The app API also applies a per-user request limit.

Example tool body:

```json
{
  "text": "Text to translate",
  "target_language": "Russian"
}
```

AI is unavailable until `HF_TOKEN` and `HF_AI_MODELS` are configured on the deployed server.


## Accounts and verified badges

Migration `002_accounts_badges.sql` adds device-account registration, premium tiers, staff badges and an audit trail. A normal account is limited to 3 saved accounts per installation; an account with an active Premium tier may register up to 5. The Android client must use a stable random installation ID as `device_id` and keep each account's token separately in secure local storage.

Set `FOUNDER_USER_ID` to the database user ID of the project owner. On startup, the API assigns that account the Founder badge. Founder and Developer can manage staff badges; Co-Developers can grant the Team badge. Premium badges cannot be assigned manually: the subscription webhook updates `users.premium_tier` and assigns the corresponding badges.

The subscription webhook accepts `none`, `premium`, `premium_plus` or `premium_elite` as `tier`. Configure a random `SUBSCRIPTION_WEBHOOK_SECRET` of at least 32 characters and send it in the `X-Subscription-Secret` header. Do not expose this secret in the Android app. The webhook is an integration point, not a payment processor; a billing provider still needs to be connected.
