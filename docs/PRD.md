# Wake Plan · 计划管理 APP — 产品需求文档 (PRD)

| 项目 | 内容 |
|---|---|
| 产品名 | Wake Plan |
| 版本 | v0.1 (草案) |
| 作者 | Mavis (协助整理) |
| 创建日期 | 2026-06-30 |
| 目标平台 | Android (API 26+,目标 API 35) |
| 形态 | 原生 App · 本地优先 · 无后端无账号 |

---

## 1. Executive Summary · 执行摘要

### 1.1 Problem Statement · 痛点

用户通常同时管理多条人生计划——健身、学习、读书、副业——但市面上的日程类应用要么只服务单一场景（待办清单 / 日历 / 习惯打卡），要么把"计划"和"日程"混在一起。一旦某项任务超时，连锁反应完全靠用户手动拖动，过程损耗极大，到一天结束时计划基本形同虚设。

### 1.2 Proposed Solution · 方案

一款**原生 Android、本地优先**的多计划时间管理 App，每个计划独立维护但共享时间轴。核心差异点：
- **时间分段 + 任务绑定**——一天先切段，再往段里塞任务。
- **任务级"自动延期"开关**——超时任务可决定是否拖动后续任务，而非全局策略。
- **当前时刻聚合视图**——同一时段多计划的任务合并显示。
- **时间线预览 + 内联冲突提示**——所见即所得，不弹干扰 modal。

### 1.3 Success Criteria · 成功指标 (MVP 上线后 4 周内)

| 指标 | 目标值 | 测量方式 |
|---|---|---|
| 任务完成率 | 周均 ≥ 70%（计划内任务完成数 / 计划任务数） | 本地埋点 + 用户统计面板 |
| 计划保持偏差 | 单任务实际完成时间 vs 计划时间，4 周后中位数绝对偏差 ≤ 10 分钟 | 计算 local DB 中 `TaskCompletion.actualEnd - task.plannedEnd` |
| 创建第一个计划耗时 | 中位数 ≤ 30 秒 | 从"新建计划"点击到首个任务保存完成 |
| 启动性能 | 冷启动到主界面可交互 ≤ 1.5s（Pixel 5 参考） | Macrobenchmark |
| 崩溃率 | Crashlytics-free 率 ≥ 99.5% | （若不上线无此指标，则改人工测试通过率 100%） |
| 用户留存 | 7 日留存 ≥ 40% | 本地 first-launch timestamp 计算 |

---

## 2. User Experience & Functionality · 用户体验与功能

### 2.1 User Personas · 用户画像

#### P1 · 自驱型多线学习者（核心）
- 25–40 岁，1–3 份工作之余搞健身 / 考证 / 写作
- 手机主力是 Android，用过 Todoist / 滴答清单 / Google Calendar
- 痛点：多 App 切换、计划乱、午后失控
- **核心诉求**：一天打开一次，看到现在该做什么；遇到卡顿一键延期

#### P2 · 重度规划者
- 已经把日 / 周计划写成 Excel / Notion 模板
- 希望把现有模板"导入"而不是手动重建
- **核心诉求**：模板机制、复制粘贴段、批量编辑

#### P3 · 习惯养成用户（次要）
- 体重管理、21 天打卡类
- **核心诉求**：重复规则、连续天数统计（v2 再覆盖）

### 2.2 User Stories · 用户故事

按优先级 P0 / P1 / P2 排列。AC 用 Given-When-Then 给出可验证条件。

#### US-01 [P0] 创建计划
**As a** 自驱型多线学习者 **I want to** 创建一个新计划（输入标题、选颜色、设起始日期）**so that** 我能把健身 / 学习 / 副业分开管理。

- **AC-01.1** Given 计划列表为空 **When** 点击 FAB "新建计划" **Then** 弹出新建计划表单，要求输入标题（必填，1–30 字符）+ 颜色选择（10 种预设）+ 起始日期（默认今天）
- **AC-01.2** Given 表单填写完毕 **When** 点击保存 **Then** 计划出现在列表中，并在主屏"当前时刻"视图空窗时显示（无任务时不阻塞）
- **AC-01.3** 标题为空时按钮置灰；超长输入提示"标题不能超过 30 字"而非弹 modal

#### US-02 [P0] 任务时间安排
**As a** 用户 **I want to** 为每个任务直接设置起止时间（如"晨跑 06:35-07:05"）**so that** 不用先建段再塞任务，任务自身就是时间安排的最小单元。

- **AC-02.1** 计划详情页直接展示任务列表（按时间排序），可新增 / 编辑 / 删除任务
- **AC-02.2** 任务字段包含：`title`（必填）、`startMinutes`（0..1440，当天分钟数）、`endMinutes`（必须 > startMinutes，可 > 1440 表示跨午夜）、`repeatRule`（仅一次 / 每天 / 工作日 / 自定义星期）、`customDays`（仅 CUSTOM 时填）、`isLocked`（boolean，默认 false）、`note`（可选）
- **AC-02.3** 同一计划内两个任务时间重叠时，编辑器内联提示"与任务 X 重叠"，但允许保存（不强制 modal）
- **AC-02.4** 跨午夜任务（如 23:00-00:30）写入时 `endMinutes` 可 > 1440，运行时按 dayOffset 折算到次日

#### US-03 [P0] 任务排序与链式延期
**As a** 用户 **I want to** 任务按时间自动排列，超时后后续任务自动顺延 **so that** 不用手动逐个调整时间。

- **AC-03.1** 计划详情页任务列表按 `(startMinutes, orderInPlan)` 排序展示
- **AC-03.2** 所有未锁定任务（`isLocked=false`）都参与链式碰撞延期；`isLocked=true` 的任务钉死在原定时点，链式延期到此停止
- **AC-03.3** 总任务时长超过可用时间时，冲突检测内联提示，但不阻止保存
- **AC-03.4** `isLocked=true` 的任务在列表中有明显的"钉死"视觉标记

#### US-04 [P0] 任务模板
**As a** 用户 **I want to** 把常用任务组合存为模板（如"标准晨练"）**so that** 新计划时直接套用。

- **AC-04.1** 计划详情页提供"另存为模板"操作
- **AC-04.2** 模板库独立 Tab 入口，预置 3 个模板：标准晨练 / 标准学习 / 周末阅读
- **AC-04.3** 从模板新建计划时，可编辑标题、颜色、起始日期；任务列表一次性灌入

#### US-05 [P0] 启动任务
**As a** 用户 **I want to** 点击任务卡片"开始"按钮 **so that** 记录实际开始时间。

- **AC-05.1** 任务卡片大点击区显示"开始"按钮；点击后写入 `TaskCompletion` 行：`taskInstanceId`, `actualStart = now`, `status = IN_PROGRESS`
- **AC-05.2** 任务进入"进行中"状态后：按钮变"完成"、底部出现倒计时（`endMinutes - now`，超时变红 + 显示 "+X 分钟"）
- **AC-05.3** 同时只允许一个任务处于 IN_PROGRESS 状态；启动新任务前若存在进行中任务，需先"完成 / 跳过"老任务（提示但不弹强制 modal——文案："X 还在进行，先完成它？"+ 两个按钮）

#### US-06 [P0] 完成任务 + 链式延期
**As a** 用户 **I want to** 完成任务后，应用根据溢出时间自动后移后续未锁定任务 **so that** 不会一路手动改时间。

- **AC-06.1** 任务完成时，计算 `overrun = max(0, actualEnd - plannedEnd)`（任务级），单位精确到分钟，向下取整
- **AC-06.2** 按时间顺序遍历同一计划同日的后续"未开始"任务：
  - 若 `isLocked=false` → 调整 `startMinutes += overrun`、`endMinutes += overrun`，继续级联
  - 若遇到 `isLocked=true` → **立即停止级联**，锁定任务及其后任务原位保留
- **AC-06.3** 写入 `TaskCompletion` 时记录受影响任务 id 列表，便于回溯
- **AC-06.4** 自动延期后通知推送：根据用户设置，可选"已延期 X 分钟 / Y 任务已推迟到 HH:mm"
- **AC-06.5** Edge case：A 计划内的延期**不传播**到 B 计划的同期任务（B 独立计时）

#### US-07 [P0] 当前时刻聚合视图
**As a** 用户 **I want to** 打开 App 直接看到当前时间正在进行的任务（跨多个计划）**so that** 一眼掌握此刻该做什么。

- **AC-07.1** 主屏布局：顶部"现在 HH:mm" + 巨型当前任务卡 + 24h 水平时间线（跨计划聚合）+ 后续任务列表；卡片显示任务名、所属计划（带颜色标签）、剩余时间 / 进行中状态
- **AC-07.2** 同一时点存在多个计划任务 → 时间线上以色块叠加展示，当前任务以最大卡片突出
- **AC-07.3** 主屏支持"今日"水平时间线，红线标记当前时刻，一眼看到后续安排
- **AC-07.4** 没有任何任务时显示空白态文案："今天还没有安排，去创建计划 →"

#### US-08 [P1] 时间线预览
**As a** 用户 **I want to** 在计划详情页看到一整天的所有任务在时间轴上的分布 **so that** 我能提前发现超载或空白。

- **AC-08.1** 计划详情页顶部统计栏 + 任务列表；未来可扩展"时间线预览"模式（水平 24h 时间轴）
- **AC-08.2** 任务以色块在时间轴上显示，长度按 `durationMinutes`，颜色按所属计划
- **AC-08.3** 支持左右滑动切换日期（默认今天 + 未来 7 天可预览）
- **AC-08.4** 点击任一色块跳转至该任务编辑界面
- **AC-08.5** 滚动到底显示"明天 / 后天"快速跳转锚点

#### US-09 [P0] 任务冲突检测
**As a** 用户 **I want to** 在新增任务时，App 提醒我与现有任务的冲突，**so that** 我能避免日程打架。

- **AC-09.1** 任务编辑表单实时检测：当前任务的 `[startMinutes, endMinutes]` 与同计划 / 其他计划的任务时间区间重叠时，编辑器底部出现折叠面板"⚠ X 个冲突"
- **AC-09.2** 展开面板显示冲突任务清单：对方计划色 + 标题 + 时间区间；用户可分别点击跳转
- **AC-09.3** **不弹模态**，冲突只是提示；保存按钮永远可点——这是产品原则：能用正确实现解决的，不用 modal 兜底
- **AC-09.4** 冲突来源区分"同计划内"（强烈警告，红色）和"跨计划"（柔和提示，黄色）

#### US-10 [P1] 本地通知
**As a** 用户 **I want to** 在任务开始前 5 / 10 分钟收到通知 **so that** 不漏掉关键时刻。

- **AC-10.1** 通知渠道（Channel）：
  - `task_starting`：任务即将开始（默认 5 分钟前，可配置 0/5/10/15）
  - `task_overdue`：任务超时未完成（每 10 分钟一次，最多 3 次）
  - `task_completed`：任务完成确认（默认关闭，可选）
  - `plan_recap`：每日 21:00 当日完成率小结（默认关闭）
- **AC-10.2** 通知触发由 WorkManager + AlarmManager（精确时间）联合实现
- **AC-10.3** Android 13+ 申请 `POST_NOTIFICATIONS` 运行时权限，引导页必须先申请
- **AC-10.4** 通知点击 → 跳转主屏并定位到对应任务

#### US-11 [P1] 数据备份与导出
**As a** 用户 **I want to** 把所有计划数据导出成 JSON 文件备份 **so that** 换机或重装不丢数据。

- **AC-11.1** 设置页"导出数据"按钮，调用系统 SAF（Storage Access Framework）让用户选保存位置
- **AC-11.2** 导出 JSON 包含全部计划、任务、模板、完成记录
- **AC-11.3** 备份文件可设 AES 加密（用户自定义 passphrase，可选）
- **AC-11.4** 同样入口支持"从备份导入"，导入前显示影响范围预览（"将影响 X 个计划、Y 个任务"）

#### US-12 [P2] 完成统计面板
**As a** 用户 **I want to** 查看过去 7 / 30 天的任务完成情况 **so that** 看到自己的坚持。

- **AC-12.1** 统计 Tab 显示：每日完成率折线图、按计划分组的堆叠柱状图、平均延期时长
- **AC-12.2** 数据来自 `TaskCompletion` 表，统计逻辑全部本地

### 2.3 Non-Goals · 非目标（明确不做）

为保护时间线和定位，下列功能**不在 MVP 范围**：
- iOS / 鸿蒙 / 平板适配（仅手机 Android）
- 云同步 / 多端同步 / 账号体系
- 协作计划 / 共享给家人朋友
- AI 推荐任务排程、智能延建议
- Google Calendar / Outlook 双向同步
- 桌面端 / Web 端
- 任务评论 / 附件 / 文件上传
- 国际化（首发仅中文简体）
- 平板 / 折叠屏专属布局
- 桌面小部件（AppWidget）

---

## 3. AI System Requirements · AI 系统要求

**本项目 MVP 不涉及 AI / LLM 功能**，该章节占位，明确说明：
- 当前 PRD 不调用任何 LLM API、无 RAG、无向量库
- 智能推荐类（"今天应该先做什么"）列入 v2.0+ 候选
- 如未来引入 AI 子模块，将独立建一版 PRD 评审

---

## 4. Technical Specifications · 技术规范

### 4.1 Architecture Overview · 架构概览

```
┌────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                            │
│  ├─ MainScreen / PlanListScreen / PlanDetailScreen    │
│  ├─ TodayScreen (聚合视图)                              │
│  ├─ TimelineScreen (时间线)                            │
│  └─ TaskEditorScreen / SegmentEditorScreen / TemplateScreen │
├────────────────────────────────────────────────────────┤
│  ViewModel Layer (UDF + StateFlow)                     │
│  └─ PlanListVM / PlanDetailVM / TodayVM / ...          │
├────────────────────────────────────────────────────────┤
│  Domain / UseCase (可选)                                │
│  └─ AutoExtendCalculator / ConflictDetector            │
├────────────────────────────────────────────────────────┤
│  Repository Layer                                      │
│  └─ PlanRepository / TaskRepository / TemplateRepo    │
├────────────────────────────────────────────────────────┤
│  Data Source Layer                                     │
│  ├─ Room DB (SQLCipher 加密)                           │
│  ├─ DataStore (用户偏好)                               │
│  └─ NotificationScheduler (WorkManager + AlarmManager)│
└────────────────────────────────────────────────────────┘
```

**关键技术决策**：
- 语言：Kotlin 2.0+
- UI：Jetpack Compose + Material 3
- 状态：StateFlow + Compose `collectAsStateWithLifecycle`
- 异步：Coroutines + Flow
- DI：Hilt（KSP 编译期）
- 导航：Navigation Compose
- DB：Room + SQLCipher（加密）
- 偏好：Jetpack DataStore Proto
- 序列化：kotlinx.serialization（JSON 备份导出）
- 时间：java.time (API 26+ via desugaring)

### 4.2 Data Model · 数据模型

```kotlin
@Entity
data class Plan(
    @PrimaryKey val id: String,         // UUID
    val title: String,
    val colorHex: String,                // "#RRGGBB"
    val startDate: LocalDate,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Entity
data class Task(
    @PrimaryKey val id: String,
    val planId: String,                  // FK -> Plan
    val title: String,
    val startMinutes: Int,               // 0..1440，跨日允许 >1440
    val endMinutes: Int,                 // 必须 > startMinutes
    val repeatRule: RepeatRule,          // enum: ONCE / DAILY / WEEKDAYS / CUSTOM
    val customDays: Set<DayOfWeek> = emptySet(),
    val isLocked: Boolean = false,       // true = 钉死，链式延期到此停止
    val orderInPlan: Int,
    val note: String? = null
) {
    val durationMinutes: Int get() = endMinutes - startMinutes
}

@Entity
data class PlanTemplate(
    @PrimaryKey val id: String,
    val title: String,
    val colorHex: String,
    val isBuiltIn: Boolean,
    val tasks: List<TemplateTask>        // @Embedded 或关联表
)

@Entity
data class TaskCompletion(
    @PrimaryKey val id: String,
    val taskId: String,
    val planId: String,
    val scheduledDate: LocalDate,        // 哪个"实例"
    val plannedStart: LocalDateTime,
    val plannedEnd: LocalDateTime,
    val actualStart: LocalDateTime?,
    val actualEnd: LocalDateTime?,
    val status: TaskStatus,              // PENDING / IN_PROGRESS / COMPLETED / SKIPPED
    val overrunMinutes: Int = 0,         // actualEnd - plannedEnd
    val affectedTaskIds: List<String> = emptyList()
)
```

### 4.3 Auto-Extend Algorithm · 自动延期算法

**触发时机**：任务状态变更 `IN_PROGRESS -> COMPLETED`

```
function applyChainExtend(completedTask, allTasks, scheduleDate):
    overrun = max(0, minutesBetween(completedTask.plannedEnd, completedTask.actualEnd))
    if overrun == 0:
        return []
    
    affected = []
    remainingTasks = allTasks
                        .where(t => t.planId == completedTask.planId
                                 && t.scheduledDate == scheduleDate
                                 && t.status == PENDING
                                 && t.startMinutes > completedTask.startMinutes)
                        .sortedBy(t => (t.startMinutes, t.orderInPlan))
    
    for nextTask in remainingTasks:
        if nextTask.isLocked:
            break                          // 钉死任务，停止级联
        nextTask.startMinutes += overrun
        nextTask.endMinutes += overrun
        affected.append(nextTask)
    
    persist(affected)
    emit ChainExtendEvent(overrun, affected)
    return affected
```

**关键约束**：
- 单计划内级联，跨计划独立
- 任一环节遇到 `isLocked=true` 即终止后续级联
- 已 `IN_PROGRESS` 的任务不参与级联（避免把用户正在进行的工作推后）
- 已 `COMPLETED / SKIPPED` 的任务不参与
- 跨午夜任务：`endMinutes` 可 > 1440，运行时按 dayOffset 折算到次日

### 4.4 Conflict Detection Algorithm · 冲突检测

```
function detectConflicts(candidateTask, allTasks):
    candidateStart = candidateTask.startMinutes
    candidateEnd = candidateTask.endMinutes
    
    samePlan = []
    crossPlan = []
    
    for other in allTasks.where(t => t.id != candidateTask.id
                              && t.scheduledDate == candidateTask.scheduledDate
                              && t.status in [PENDING, IN_PROGRESS]):
        otherStart = other.startMinutes
        otherEnd = other.endMinutes
        
        if otherStart < candidateEnd && otherEnd > candidateStart:
            if other.planId == candidateTask.planId:
                samePlan.append(other)
            else:
                crossPlan.append(other)
    
    return ConflictReport(samePlan, crossPlan)
```

**冲突检测的判断区间**：`[aStart, aEnd)` 半开区间——aEnd 时间点和 bStart 重合不算冲突。

### 4.5 Integration Points · 集成点

| 集成项 | 用途 | 依赖 |
|---|---|---|
| Android Notification API | 推送任务提醒 | 系统 NotificationManager + Channel API |
| AlarmManager (setExactAndAllowWhileIdle) | 精确触发"任务开始前"提醒 | 配合 WorkManager 双保险 |
| WorkManager | 周期任务（超时检查、每日 recap） | androidx.work |
| Storage Access Framework | 备份导出 / 导入 | 系统 SAF Intent |
| AndroidX Security Crypto | 加密 SharedPreferences | 存用户偏好与备份 passphrase hint |
| SQLCipher | 数据库加密 | net.zetetic:android-database-sqlcipher |
| Macrobenchmark / Benchmark | 启动性能指标 | androidx.benchmark |

**无需集成**：
- 无后端 API
- 无第三方账号登录
- 无 Firebase（如果不上线则避免一切 Google 服务依赖）
- 无 Crashlytics（自己跑测试 + 用户反馈）

### 4.6 Security & Privacy · 安全与隐私

**核心原则：本地优先，零外联**。
- App 不发送任何网络请求（即使统计页也是本地计算）
- Room 数据库使用 SQLCipher 加密（AES-256），密钥存 Android Keystore
- 备份导出文件 AES-256 加密（PBKDF2 派生密钥，自定义 passphrase）
- 通知内容不向云端泄露（无 GCM，纯本地 AlarmManager）
- 不申请位置 / 通讯录 / 相册权限
- 必要权限最小集：
  - `POST_NOTIFICATIONS`（Android 13+）
  - `SCHEDULE_EXACT_ALARM`（Android 12+，精确闹钟）
  - `USE_EXACT_ALARM`（Android 13+，可选）
  - `WAKE_LOCK`（WorkManager 隐含）
  - `RECEIVE_BOOT_COMPLETED`（重启后恢复闹钟）
- 隐私政策：在设置页内置一份极简版（不上传）

---

## 5. Risks & Roadmap · 风险与路线图

### 5.1 Phased Rollout · 阶段性发布

| 阶段 | 范围 | 预计周期 | 发布形式 |
|---|---|---|---|
| **v0.1 MVP** | 计划 CRUD · 任务时间安排 · 模板（基础）· 开始/完成/跳过 · 链式延期 · 当前时刻视图（含时间线）· 冲突提示（内联）· 本地通知（任务开始+超时）· JSON 备份导出 | 8–10 周（个人节奏） | APK 直装 / 内测 |
| **v0.2** | 时间线预览 · 模板库 UX 打磨 · 通知渠道细化 · 完成统计面板 | 4–6 周 | 同上 |
| **v0.3** | 桌面小部件（AppWidget）· 快捷设置磁贴（Quick Settings Tile） | 3–4 周 | 同上 |
| **v1.0 正式** | 隐私政策完善 · 应用市场上架（小米/华为/Play 商店）· 完整 E2E 测试 | 4 周 | 应用市场 |
| **v1.1+（候选）** | Google Calendar 同步（单向）· 平板适配 · 国际化（英文） | TBD | — |
| **v2.0（候选）** | 可选云同步（用户自建服务器 / WebDAV）· AI 排程建议 | TBD | — |

> **说明**：用户表态"不设死线，慢慢做"，所以上述周期是参考估算，遇到卡点随时调整。

### 5.2 Technical Risks · 技术风险

| 风险 | 概率 | 影响 | 缓解策略 |
|---|---|---|---|
| Android 厂商杀后台，闹钟不响 | 高 | 高 | WorkManager + AlarmManager 双触发，关键提醒使用 Foreground Service；提供"测试通知"按钮引导用户加白名单 |
| Android 13+ 通知权限被拒 | 中 | 高 | 启动引导页必须申请；拒绝后设置页红字提示；提供去系统设置深链 |
| 自动延期算法边界 case | 中 | 中 | 至少 90% 覆盖率单元测试；提供"撤销最后一次延期"操作 |
| Room 数据库迁移 | 低 | 高 | 严格版本号 + 自动化迁移测试（androidx.room.testing） |
| 备份导出跨设备兼容（不同 Android 版本时间格式） | 低 | 中 | 使用 ISO-8601 + java.time；版本号字段嵌入备份 JSON |
| 单设备 SQLite 容量上限 | 低 | 低 | 设计目标 5 年 ≤ 50MB，留足余量 |
| 跨午夜任务计算 bug | 中 | 中 | 单独抽 `TimeWindowCalculator` 用例；任务编辑器预览实际日期归属 |

### 5.3 Open Questions · 待定问题

> 进入开发前需要确认的开放项：

1. **颜色预设数量**：MVP 用 10 种够不够？要不要支持自定义色？
2. **通知时间点默认值**：任务开始前 5 分钟是否合适，还是 10 分钟？
3. **超时提醒频率**：10 分钟一次是否过频？要不要做成可配置？
4. **自动延期是否可一键撤销**：MVP 是否需要？还是先不做？
5. **重复规则**：MVP 要不要支持"每周一三五"这种自定义？还是仅一次 / 工作日 / 每天？
6. **统计面板**：v0.1 就上，还是放到 v0.2？
7. **本地化**：纯中文 MVP？还是顺手英文（strings 抽出来）？

---

## Appendix · 附录

### A.1 术语表

| 术语 | 定义 |
|---|---|
| 计划 (Plan) — 数据模型 | 一组目标的容器，如"健身计划"。一条计划下含多个任务。**注意**：这是数据模型实体名（Kotlin `data class Plan`），与产品名 "Wake Plan" 区分 |
| 任务 (Task) | 计划下的具体可执行项，自带起止时间（`startMinutes`/`endMinutes`）、重复规则、锁定标记，如"晨跑 06:35-07:05" |
| 任务实例 (TaskCompletion) | 任务在某一天的具象化执行记录（同一任务在周一和周二是两个实例） |
| 链式延期 (Chain Extend) | 当前任务超时时，把同计划后续未锁定任务依次后移到完成时间的机制；遇到 `isLocked=true` 的钉死任务即停止 |
| 钉死 (Locked) | 任务的 `isLocked=true` 属性，表示该任务时间固定，链式延期碰到它就停止传播 |
| 当前时刻视图 (TodayView) | 主屏，按当前时间过滤出跨计划的活跃任务，展示时间线和即将开始的任务 |

### A.2 参考竞品

- **Todoist**：强待办，弱时间安排
- **滴答清单**：中日韩用户多，强提醒，但多计划体验差
- **Google Calendar**：强日历，弱"任务级操作"
- **TimeBloc** (iOS)：时间块 + 任务（仅 iOS，无 Android）
- **Sectograph**：圆形时间线（Android 老牌，体验陈旧）

本产品差异化：扁平任务链式延期 + 多计划时间线聚合 + 钉死机制 + 零外联。

### A.3 文档版本

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.1 草案 | 2026-06-30 | 初稿 |

---

> 文档结束 — 如需修改章节或拆分更细的 sub-PRD，告诉我具体方向。
