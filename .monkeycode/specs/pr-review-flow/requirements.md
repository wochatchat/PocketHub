# Requirements Document — PR Review Flow

## Introduction

为 PocketHub 的 PR 详情页补齐完整 review 流程，让协作者在手机上即可 approve / request changes / 评论 / 解决/回复行内评论，与网页版体验对齐。

## Glossary

- **PullRequestReview**: 一次完整 review 提交（含 state: APPROVED / CHANGES_REQUESTED / COMMENTED / DISMISSED/PENDING），对应 GitHub `/reviews` 资源
- **ReviewComment**: 行内评论，锚定到 PR diff 的某文件某行，对应 GitHub `/pulls/{n}/comments`
- **Issue Comment**: PR 顶层讨论评论（非行内），跟 Issue 评论同一套 API
- **Thread Resolve/Unresolve**: 把一个行内评论 thread 标记为已解决/重新打开，对应 `/comments/{id}` 上 `pull_request_review_thread` 的 resolve action（GraphQL）

## 现状

### 已具备
- API 层：`getPullRequestReviews`、`createPullRequestReview(body=ReviewRequest(event, body))`、`listPullRequestReviewComments`、`createPullRequestReviewComment`
- VM 层：`reviewComments` StateFlow，`postInlineComment()` 乐观更新方法
- UI 层：Files tab 显示行内评论气泡，但没有"发起 review"的入口
- 评论 input：仅有顶层 issue comment 输入框

### 缺失
- 顶部 review 提交入口（approve / request changes / comment 三选一 + body）
- 已有 reviews 列表展示（state、author、body、提交时间）
- 行内评论的**回复**和** resolve / unresolve**
- 行内评论的**编辑**和**删除**
- 已 review 状态对 merge 按钮的影响（如 CHANGES_REQUESTED 状态下做 soft warning）

## Requirements

### R1. 发起 Review 提交

**User Story:** AS 仓库协作者, I want 在 PR 详情页直接提交 APPROVE / CHANGES_REQUESTED / COMMENT 三种 review, so that 不用切到网页版即可完成 review 决策。

#### Acceptance Criteria

1. WHEN 用户在 PR 详情页点击右上角 "Review" 图标, 系统 SHALL 弹出底部对话框包含: review event 类型(Comment/Approve/Request Changes) 单选 + body 多行输入框 + 提交按钮。
2. WHEN 用户点击 "Submit review", 系统 SHALL 调用 `createPullRequestReview` 提交并刷新 reviews 列表。
3. IF 调用 `createPullRequestReview` 失败, 系统 SHALL 显示原样错误消息, 保留对话框与用户输入, 允许用户重试。
4. WHILE 三种 review 事件中至少一种已被当前用户提交过 APPROVED 或 CHANGES_REQUESTED, 系统 SHALL 在 Review 入口图标上显示徽章提示 already reviewed。
5. WHEN review 成功提交后, 系统 SHALL 把对话框关闭, 在 reviews 列表对应记录置顶, 并触发一次 PR refresh 让 `reviewDecision` 反映最新状态。

### R2. Review 列表展示

**User Story:** AS 仓库协作者, I want 在 PR 详情页查看已有的所有 reviews, so that 了解其他人已经给了什么意见。

#### Acceptance Criteria

1. WHEN 用户进入 PR 详情页, 系统 SHALL 在 Reviews section 渲染所有现存 review: 每项含 reviewer 头像、登录名、state 标签(MEMS/APPROVED/CHANGES_REQUESTED/COMMENTED/PENDING)、body 预览(行内评论汇总)、提交时间。
2. WHEN 某 review 的 body 非空, 系统 SHALL 把 body 折叠显示, 点击展开查看全文。
3. IF `getPullRequestReviews` 调用失败, 系统 SHALL 在 reviews section 显示错误条与重试按钮。

### R3. 行内评论回复与 Resolve

**User Story:** AS 仓库协作者, I want 在 PR 行内评论下回复他人评论并标记已解决, so that 在 diff 维度上沟通问题闭环。

#### Acceptance Criteria

1. WHEN 用户点击一条行内评论, 系统 SHALL 展开 thread 回复框与 resolve / reply 操作按钮。
2. WHEN 用户在回复框输入文本并提交, 系统 SHALL 调用 `createPullRequestReviewComment(inReplyTo=原评论 id)` 提交, 把新评论追加到 thread 中。
3. WHEN thread 没有未解决的子评论, 系统 SHALL 把该 thread 标记为已解决; 已解决的 thread 头部 SHALL 显示 "Resolved" 标签并在 resolve 按钮上切换为 "Unresolve"。
4. IF 回复或 resolve 调用失败, 系统 SHALL 显示错误条并保留输入内容; 不 SHALL 把临时状态直接写入 UI。

### R4. 行内评论编辑与删除

**User Story:** AS 评论作者, I want 编辑或删除自己发过的行内评论, so that 修正错误或清理无效意见。

#### Acceptance Criteria

1. WHEN 用户点击自己发布的行内评论的"更多"按钮, 系统 SHALL 显示 Edit / Delete 两个选项。
2. WHEN 选择 Edit, 系统 SHALL 在原位置展开内联编辑框, 预填现有 body, 提交调用 `PATCH /pulls/comments/{id}` 更新; 成功后原位置刷新内容。
3. WHEN 选择 Delete, 系统 SHALL 弹出二次确认, 确认后调用 `DELETE /pulls/comments/{id}`, 成功后从 thread 移除该条; 若删除的是 thread 根评论, 系统 SHALL 把整 thread 移除。
4. IF 编辑/删除操作 target 评论非当前用户作者, 系统 SHALL 隐藏 Edit / Delete 入口(由 UI 层判定)。

### R5. Merge 联动

**User Story:** AS 仓库协作者, I want 当有 CHANGES_REQUESTED review 时, merge 按钮给出警示, so that 避免忽视 review 意见。

#### Acceptance Criteria

1. WHILE 当前 PR 至少存在一条未 dismiss 的 CHANGES_REQUESTED review, 系统 SHALL 在 merge 按钮上方显示预警条 "X reviewers requested changes"。
2. WHEN 预警条显示时, 系统 SHALL 仍允许 merge(不阻塞, 由仓库 branch protection 决定), 但点击 merge 前弹出二次确认对话框说明。

## Out-of-scope

- 多 reviewers 批量请求 review (Reviewers 管理 — 留在下个 feature)
- Draft PR 转 ready (Draft 切换)
- 复用网页版 rich diff(显示左右双栏) — 移动端空间窄仍走 unified diff
- Review suggestion("建议修改"应用) — 跨文件应用补丁超出本 feature 范围
- Review 的 dismiss / 编辑已提交 review body — 已提交 review state 不可改, 仅可 dismiss (留作下个 feature)

