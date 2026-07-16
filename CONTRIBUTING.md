# 贡献指南 · Contributing to PocketHub

谢谢你想来一起做 PocketHub ❤️ 下面几条看完就能直接开干。

## 1. 先翻过老 issues / PRs

提新 issue 前请先在 [现有列表](https://github.com/wochatchat/PocketHub/issues?q=is%3Aissue) 里搜一下你的关键词，避免出现重复。

## 2. 提 Issue

仓库已内置三套 [Issue Form 模板](https://github.com/wochatchat/PocketHub/issues/new/choose)：

| 图标 | 模板 | 用在哪 |
|------|------|--------|
| 🐛 | Bug 报告 | 现有功能报错 / 渲染异常 / 行为不符合预期时 |
| ✨ | 功能建议 | 想加新能力 / 改进现有交互 |
| 📱 | 兼容性 / 设备问题 | 你的机型 / ROM 上跑不对 |

请尽量填全模板里的字段——尤其是**复现步骤**和**截图**，信息不全的 issue 我可能会先回来追问而不是直接动手。

## 3. 提 PR（代码贡献）

1. Fork 仓库 → 本地 clone → 切一个 `feat/your-feature` 或 `fix/your-fix` 分支。
2. 能跑通 `./gradlew :app:assembleDebug` 再说。
3. UI 改动务必在真机或模拟器上肉眼看过一遍，不只满足于 CI 通过。
4. 提 PR 的时候会自动套用 .github/PULL_REQUEST_TEMPLATE.md，照着勾选自查项。
5. 如果改了依赖，记得同步到 `gradle/libs.versions.toml`，不要在 `app/build.gradle.kts` 里直接写硬编码版本。
6. 大改动（新模块、改架构、改 API 协议）建议先开 issue 讨论，达成一致再动工。

## 4. 代码风格

- 单文件尽量控制在 800 行内，超了拆分。
- Compose 组件用 `@Composable` 注解 + `private fun`，对外暴露的走顶层 `fun`。
- View state 用 `StateFlow`，不直接拿 ViewModel 当 single source 之外的东西。
- 公共类型别放在 `MainActivity.kt` 里塞塞塞，按 `data/`、`ui/<feature>/`、`domain/` 分层。

## 5. CI

推到 `main` 会自动触发 [Build Release APK](https://github.com/wochatchat/PocketHub/actions/workflows/build.yml)，
成功后会自动新版本 + 把 APK commit 回 `apk/` 目录。本地可以通过 `./build.sh --no-bump` 测试构建。

## 6. 行为约定

- 主分支只接受已通过 CI 的内容。
- 合并请使用 "Squash and merge"，让历史保持清爽。
- 不要在 PR 里夹带与本主题无关的格式化提交（比如顺手格式化整个 README）。

## 7. 联系方式

- Issue / Discussion 是首选渠道。
- 紧要的事也可以 @wochatchat 私信。
