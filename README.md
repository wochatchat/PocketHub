# PocketHub

A well-crafted open-source GitHub client for Android, built with Kotlin + Jetpack Compose + Material 3.

> Status: **Work in progress** (V1 — core client).

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
Tabs: Overview (README) · Code (file tree) · Issues · PRs · Releases · Actions · Wiki · Projects
*(Releases / Actions / Wiki / Projects are placeholder tabs for V2.)*

### Theming
- **Dark (Linear-inspired)** — default, compact, calm accent
- **Light (GitHub Primer-inspired)** — airy, warm cards

### Offline
- Room local cache on all fetched data
- Cache-first display + silent background refresh
- Local retry queue for failed operations

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

## Contributing
- 发现 bug 或有想法？直接 [提 issue](https://github.com/wochatchat/PocketHub/issues/new/choose)，已内置如下模板帮你把话讲清楚：
  - 🐛 **Bug 报告** — 仓库详情/下载/通知等任何模块炸了或显示乱码，选这个
  - ✨ **功能建议** — 想加的能力或改进，选这个
  - 📱 **兼容性 / 设备问题** — 在你的机型/ROM 上的诡异表现，选这个
- 想直接贡献代码？先 fork → branch → PR，PR 模板里也列了自查清单。
- 详细约定见 [CONTRIBUTING.md](CONTRIBUTING.md)。
