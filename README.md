# PocketHub

<p align="center">
  <a href="README_zh.md"><img src="https://img.shields.io/badge/%E4%B8%AD%E6%96%87%E7%89%88%E6%9C%AC-README_zh.md-blue" alt="Chinese Version"></a>
</p>

A well-crafted open-source GitHub client for Android, built with Kotlin + Jetpack Compose + Material 3.

> Status: **Work in progress** (V1 — core client).

**[中文文档](README_zh.md)** · **[GitHub Releases](https://github.com/wochatchat/PocketHub/releases)**

---

## Features

### Authentication
- Personal Access Token (PAT)
- OAuth App (built-in default client + custom client override)

### Navigation (4 Tabs)
1. **Explore** — Trending repos/developers, followed activity
2. **Repositories** — Your repos + Starred, with filters and sorting
3. **Notifications** — Grouped by repository, unread/read
4. **Profile** — Multi-account, drafts, settings

Global search is available from the top bar.

### Repository Detail
Tabs: Overview (README) · Code (file tree) · Issues · PRs · Commits · Releases · Actions
*(Wiki / Projects are not yet available — planned for V2.)*

### Theming
- **Dark (Linear-inspired)** — default, compact, calm accent
- **Light (GitHub Primer-inspired)** — airy, warm cards

### Offline
- Room local cache on the main read paths (repos, issues, releases, trending, feed)
- Cache-first display with per-entry TTL
- Background system alerts for new unread notifications (WorkManager, deduplicated)

### Self-Update
- On launch and in Settings, the app polls the project's GitHub Releases
- A newer stable release triggers an in-app dialog (download / ignore this version / remind later)
- Ignored versions won't prompt again until a newer one ships
- Pre-releases are never surfaced automatically

### Multi-account
- Sign in with multiple GitHub accounts simultaneously
- Quick switch between accounts

## Tech Stack
- Kotlin + Coroutines + Flow
- Jetpack Compose + Material 3
- AndroidX (Lifecycle, ViewModel, Navigation Compose)
- Room (local persistence)
- Hilt (DI)
- OkHttp + Retrofit (GitHub REST API v3)
- Coil (image loading)
- DataStore (preferences / settings)

## License
Apache 2.0 (see [LICENSE](LICENSE)).

## Acknowledgements
- [@Wxjxpp](https://github.com/Wxjxpp) contributed the core code and design ideas for several features, ported from [PR #32](https://github.com/wochatchat/PocketHub/pull/32):
  - Configurable **DNS over HTTPS** with a built-in provider picker (see [commit 00976c7](https://github.com/wochatchat/PocketHub/commit/00976c7))
  - Preset **download accelerator mirrors** with a real-throughput speed test (see [commit f54348e](https://github.com/wochatchat/PocketHub/commit/f54348e))
  - The **self-hosted OAuth exchange backend** protocol (`/config` + `/oauth/exchange`) and the app's backend client (see [commit f8fbb47](https://github.com/wochatchat/PocketHub/commit/f8fbb47))
  - The **OAuth `state` CSRF check**, plus two login-flow fixes: callback delivery via `onNewIntent` ([2a878e3](https://github.com/wochatchat/PocketHub/commit/2a878e3)) and a shared `LoginViewModel` between the activity and the login screen ([dd8c6cf](https://github.com/wochatchat/PocketHub/commit/dd8c6cf))

## Contributing
- Found a bug or have an idea? Open an [issue](https://github.com/wochatchat/PocketHub/issues/new/choose) — templates are built-in.
- Want to contribute code? Fork → branch → PR.
- See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## ☕ Support / 打赏支持

If PocketHub helps you, consider buying me a cup of coffee!

<p align="center">
  <img src="https://raw.githubusercontent.com/wochatchat/PocketHub/main/.github/donate.png" alt="Buy me a coffee QR" width="240" />
</p>

<p align="center">
  <strong>Thanks for your support!</strong> 💖
</p>

<p align="center">
  <a href="README.md">English</a> · <a href="README_zh.md">中文</a>
</p>
