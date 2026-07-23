# 用户指令记忆

本文件记录了用户的指令、偏好和教导，用于在未来的交互中提供参考。

## 格式

### 用户指令条目
用户指令条目应遵循以下格式：

[用户指令摘要]
- Date: [YYYY-MM-DD]
- Context: [提及的场景或时间]
- Instructions:
  - [用户教导或指示的内容，逐行描述]

### 项目知识条目
Agent 在任务执行过程中发现的条目应遵循以下格式：

[项目知识摘要]
- Date: [YYYY-MM-DD]
- Context: Agent 在执行 [具体任务描述] 时发现
- Category: [运维部署|构建方法|测试方法|排错调试|工作流协作|环境配置]
- Instructions:
  - [具体的知识点，逐行描述]

## 去重策略
- 添加新条目前，检查是否存在相似或相同的指令
- 若发现重复，跳过新条目或与已有条目合并
- 合并时，更新上下文或日期信息
- 这有助于避免冗余条目，保持记忆文件整洁

## 条目

GitHub 代码归属与默认推送目标
- Date: 2026-07-22
- Context: 用户提交代码时说明 "wochatchat 账号的代码库才是我的代码库"
- Category: 工作流协作
- Instructions:
  - 本项目对应的 GitHub 仓库是 wochatchat/PocketHub（origin 已指向该仓库），这是用户本人的代码库
  - 提交代码时若用户未特别说明，默认推送到此 GitHub 仓库（wochatchat 的 origin），不要推送到任何其他账号或仓库
  - 改完代码并自检无误后，直接提交并推送到主分支（main）；不特意指定新开分支时，一律走主分支，不额外创建 feat/fix 分支
  - 仅在用户明确要求新开分支时才创建分支
  - 推送前先尝试 `git pull --no-rebase origin main` 合入远端最新的 CI 自动 commit，避免 push 被拒

持续自动改进工作模式
- Date: 2026-07-23
- Context: 用户指示 "后续持续改进功能直到我打断你，所有判断和决定由你决定"
- Category: 工作流协作
- Instructions:
  - 在用户明确表示"持续改进直到打断"时，自主挑选 PocketHub 待改进项，按 backlog 优先级推进
  - 每改完一项立即编译、自检、提交、推送到 main（遵循上面的主分支策略），不需要再次征求确认
  - 提交完整批次后继续对照 GitHub 网站功能做差距分析，主动识别新缺口并补做
  - 在合适时机（对话上下文已较积累、新一批工作即将开始）压缩对话历史，并把重要项目知识写入 MEMORY.md
  - 遇到不确定如何取舍的设计选项且对未来架构有不可逆影响时，仍然按用户原约定先做决定；只有违反安全红线、会破坏数据的情况例外
  - 这条指令的 scope 限于当前会话；用户随时一句话打断即停

PocketHub 项目构建/打包验证习惯
- Date: 2026-07-23
- Context: Agent 在长时间 Android 开发流程中确认的验收方式；2026-07-23 用户调整验证策略
- Category: 构建编译
- Instructions:
  - 改完 Kotlin 后用 `./gradlew :app:compileDebugKotlin` 快速验证编译错误即可；通过即可提交推送，不再每次跑 `./gradlew :app:assembleDebug` 出 APK
  - 编译命令需 `timeout 600` 包装以避免长时阻塞
  - `./gradlew` 命令要以 /workspace 为 workdir


