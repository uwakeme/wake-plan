# Wake Plan 代码审查报告

| 项目 | 内容 |
|---|---|
| 审查日期 | 2026-10-10 |
| 复核日期 | 2026-10-10（经人工逐条复核，本版为修订版 v2） |
| 审查范围 | 业务逻辑 / 功能完整性 / 代码实现三层 |
| 代码版本 | v0.6.0（versionCode 9，commit d9636c1） |
| 审查方式 | 静态代码审查（只读） |

> **v2 修订说明**：v1 经人工复核后，发现 §3.3 定性错误（漏读 KDoc 声明的设计意图）、多处汇总数字失实、后果描述夸大。本版已逐条核实并修正；被推翻的结论与理由保留在附录 A，供二次采信时参考。

> **整改状态（2026-10-27 逐项复核）**：所列问题已全部处置，对应关系如下。
>
> | 章节 | 状态 | 对应提交 |
> |---|---|---|
> | §3.1 单进行中守门 | 已修：方案 A 落在 TodayViewModel；2026-10-27 复核发现专注页入口可绕过，已在 FocusTimerController.start 补同口径守门并补测试 | db8ce57、27d7019 |
> | §3.2 撤销补重排闹钟 | 已修（Start/Skip 分支均 refresh，带回归测试） | db8ce57 |
> | §4.1 统计分母排归档 | 已修（含 2 个单测） | c57b02c |
> | §4.2 Canvas 未缓存计算 | 已修：排序、时间文案、标题前缀量测全部外提到组合期 remember | 3224f60 |
> | §4.3 focus_sessions 备份 | 已修：导出/导入/映射/清库顺序全链路；格式版本保持 1，靠默认字段向后兼容 | dcf6c5c |
> | §5.1 测试缺口 | 部分收口：新增 FocusTimerControllerTest 11 例（守门/记账/阶段流转/abandon 契约）；ScheduleManager 24h 跳过已有 ScheduleManagerTest 锁定；notification 6 个 Receiver/Worker 维持零测试（框架胶水层，有意暂缓） | d04d28f |
> | §5.2 recordRound 去重 | 已修（合并为单一 suspend 实现） | 7be237c |
> | §5.3 requestCode 防负 | 已修（`and 0x7FFFFFFF`，含 3 个单测） | fdaa5ba |
> | §5.4 SettingsScreen 过长 | 已修：主函数 742 → 约 440 行，通用下拉行与通知/备份/专注三区块抽为私有组件 | 2baa75e |
> | §6.1 进行中任务滞留 | 维持现状（KDoc 文档化设计），待后续迭代决策 | — |
>
> 全量单测 323 例通过；文中规模数据以本仓库当前实际为准。

---

## 一、审查口径说明

本次审查**不以 PRD、设计稿或路线图作为验收基准**。

这些文档是项目初期（v0.1，2026-06-30）的设计草案。项目已开发至 v0.6，功能演进和范围调整是开发过程中的正常现象，实现与历史文档不一致**本身不构成问题**。

审查依据以下六条口径：

1. 代码注释 / KDoc 声明的契约与实际实现是否一致
2. 同一概念在不同模块的处理口径是否一致
3. 对称操作是否成对（取消有取消，恢复就要有恢复）
4. 状态机是否守住了自己声明的不变量
5. 用户路径上是否存在走不出来的死角
6. 性能热点、资源泄漏、测试盲区

> **口径补充（v2）**：第 1 条是**双向**的 —— 不仅代码要与注释一致，**当注释明确声明了设计意图时，该意图本身不是缺陷**。定性前必须先读目标函数的 KDoc 与函数体注释，确认是有意设计还是遗漏。

---

## 二、总体结论

代码工程质量明显高于同体量独立项目：分层清晰、注释密度高、迁移纪律严格、加密方案完整。

**未发现阻断性缺陷。** 需处理的问题共 6 项，其中 2 项 P0、3 项 P1、1 项 P2，另有 3 项设计讨论项。

**规模数据（物理行数，`Get-Content .Count` 口径）**：

| 范围 | 文件数 | 行数 |
|---|---|---|
| main | 124 | 16241 |
| test | 34 | 6019 |

单文件较大者：TodayScreen 1406、SettingsScreen 1307、TodayViewModel 313。

---

## 三、P0 问题

### 3.1 状态机不变量未被强制：允许多个任务同时进行中

**依据**：`TaskStatus.IN_PROGRESS` 的 KDoc 明确声明「同时只允许 1 个」（`domain/model/Enums.kt:22`），但代码中无任何强制逻辑。

**问题**：`TodayViewModel.onStartTask()`（`ui/today/TodayViewModel.kt:238-250`）仅检查**目标任务自身**状态：

```kotlin
fun onStartTask(completionId: String) {
    viewModelScope.launch {
        val c = taskCompletionRepository.getById(completionId) ?: return@launch
        if (c.status != TaskStatus.PENDING) return@launch  // 只守自己的门
```

UI 层无拦截：`TodayScreen.kt:325`、`:353`、`:377` 三处 `TodayTaskCard` 均直接透传 `onStart`。

**复现路径**：任务 A 点「开始」→ 不完成 → 任务 B 点「开始」→ 两个任务同时 `IN_PROGRESS`。

**连锁后果**：

| 受影响点 | 表现 |
|---|---|
| `CascadeDelayCalculator.kt:108-112` | 两个进行中任务都按 `max(plannedEnd, now)` 推下游，两条链同时生效，结果不可预期 |
| `TodayScreen.kt:203` | 主卡 `firstOrNull` 优先取列表首个进行中任务（列表按时间有序，取值确定，但可能非用户预期的那个） |
| `StatsUseCase.kt:196` | 统计数据出现两个进行中 |

**修复建议（v2 修订）**：v1 曾写「提示但不阻断操作」，该建议与「守住不变量」自相矛盾 —— 不阻断就守不住。修正为二选一，由产品定调：

- **方案 A（守不变量）**：检测到已有进行中任务时阻止启动，引导用户先完成或跳过旧的。符合 KDoc 声明
- **方案 B（改不变量）**：若产品认为多进行中可接受，则需同时修改 KDoc 声明、并修正 `CascadeDelayCalculator` 使多条链互不干扰、`TodayScreen` 主卡选取规则显式化

---

### 3.2 对称操作缺失：撤销「开始 / 跳过」未恢复闹钟

**问题**：`TodayViewModel.undo()` (`:271-285`) 仅对 `Complete` 做了完整恢复：

```kotlin
taskCompletionRepository.upsert(action.previous)
if (action is UndoableAction.Complete) {
    taskRepository.resyncPlanCompletions(...)
    scheduleRefresher.refresh(...)   // 仅此分支重排闹钟
}
```

| 动作 | 取消侧 | 撤销恢复侧 |
|---|---|---|
| Start | `cancelStartingAlarm` ✓ (`:247`) | 未重排 ✗ |
| Skip | `cancelStartingAlarm` ✓ (`:266`) | 未重排 ✗ |
| Complete | — | 完整恢复 ✓ (`:276-283`) |

**实际影响（v2 下调，重要）**：**并非「静默消失」**。撤销后任务回到 `PENDING` 且仍显示在列表中；以下任一动作都会通过 `ScheduleRefresher.refresh` 自愈并恢复闹钟：

- 重新进入 Today 屏
- 完成任意任务（`CompleteTaskUseCase.persistCascade` 触发 refresh）
- 跨天 tick（`TodayViewModel.kt:139-153`）

因此真实影响范围限于**本次停留会话内**：用户在当前会话中不会再收到该任务的开始提醒。严重度因此从「任务静默消失」下调为「本次会话内提醒缺失，会话切换后自愈」。

**建议**：仍建议补齐对称性（`undo()` 非 Complete 分支加 `scheduleRefresher.refresh`），但优先级可降为 P1。

---

## 四、P1 问题

### 4.1 归档计划过滤口径不一致

**问题**：同一概念（有效计划）在两处处理规则不同。

完整验证链：

| 环节 | 位置 | 事实 |
|---|---|---|
| 数据源 | `PlanDao.observeAll()` | 无过滤，返回全量计划 |
| 传递 | `StatsViewModel.kt:63` | 原样传入全量 |
| 消费 | `StatsUseCase.kt:235-240` | 分母推导**未排除 archived** ✗ |
| 对照 | `ScheduleRefresher.kt:60` | 已不再为归档计划铺实例 |

`TodayViewModel.kt:191` 正确过滤（`it.isActive && !it.archived`），统计侧未同步。

**后果**：归档计划已不再产生任何实例与完成记录，但分母仍按其任务定义推导 → 完成率被无声拉低。

**建议**：在 `StatsUseCase.dayBucket()` 的 `expected` 推导中补充 `!plan.archived`。

---

### 4.2 时间线 Canvas 中的未缓存计算

**代码位置属实，量级需修正（v2）**。

三处未缓存的计算：

**① 排序写在 draw lambda 内**（`:946-947`）

```kotlin
val allCompletions = planItems.flatMap { it.completions }
    .sortedBy { it.plannedStart }
```

同文件 `:791-796` 已用 `remember(planItems)` 做了正确处理，说明作者知晓缓存模式，此处遗漏。

**② 逐字符文本测量**（`:1011-1016`）

```kotlin
for (i in taskTitle.indices) {
    val candidate = taskTitle.substring(0, i + 1)
    val m = textMeasurer.measure(candidate, style = blockTextStyle)
    if (m.size.width.toFloat() > titleSpace) break
    shown = candidate
}
```

每次重绘对每个任务标题做 O(len) 次测量，且每次新建 substring。

**③ 高频小分配**：`String.format`（`:990-993`）逐任务重建时间字符串。

**重绘频率修正（v2）**：v1 称「每帧 100 次测量」不准确。实际重绘条件为 —— `now` 每分钟才变化一次（MinuteTicker），横向滚动通过 `horizontalScroll`（`:851`）的**平移实现，不触发重绘**。因此实际是**约每分钟一次**，10 任务 × 10 字约 100 次测量，非 60fps 热点。

**结论**：属代码整洁性问题（应缓存），但**不是当前性能瓶颈**，无需紧急处理。若后续时间线增加任务密度或引入动画再行优化。

---

### 4.3 focus_sessions 不参与备份

**问题**：`data/backup/` 全目录检索 `focus` **零匹配**。`BackupData` 仅含 plans / tasks / completions / templates 四类。

计划、任务、模板、历史完成记录均可导出导入，唯独 v0.6 新增的专注记录不能。

**后果**：用户换机后，最新的专注数据全部丢失。

**建议**：`BackupModels` + `BackupMapper` 模式已成熟，补充 DTO 与映射即可。注意**备份格式版本**与**DB schema 版本**是两套独立版本机制，勿混用。

---

## 五、P2 及其他

### 5.1 测试缺口

总数 296 个 `@Test`（准确）。实测分布：

| 包 | 用例数 |
|---|---|
| domain/usecase | 86 |
| data/backup | 73 |
| ui/today | 24 |
| ui/settings | 14 |
| notification（合计） | 29 |
| data/security | 10 |
| data/prefs | 10 |
| data/export | 8 |
| domain/model | 6 |
| data/repository | 6 |
| ui/common | 5 |
| ui/plan | 5 |
| **focus** | **3** |

**focus 包严重不足**：3 个用例仅覆盖 `FocusPhaseMachine` 纯函数。核心计时逻辑 `FocusTimerController`（258 行）与 `FocusTimerService`（183 行）**零测试**，`FocusViewModel` 亦无测试。

**notification 包偏科**：29 个用例集中在 `NotificationContentBuilder`(13)、`ScheduleRefresher`(7)、`NotificationChannels`(5)、`RecapScheduler`(4)；而 `StartingAlarmReceiver`、`OverdueCheckWorker`、`OverdueWorkScheduler`、`RecapWorker`、`CompletionNotifier`、`NotificationPermissionChecker` **全部零引用**。

**特别关注**：`ScheduleManager.kt:59` 跳过 24 小时外的任务，次日闹钟依赖 `TodayViewModel` 的分钟 tick（`:139-153`）触发重排。用户整晚未打开 App 时该链路的可靠性需测试锁定。

### 5.2 FocusTimerController 代码冗余

`recordRound`（`:209-223`）与 `recordRoundInternal`（`:225-240`）构造逻辑完全重复（FocusSession 构造逐字段一致）。差异在于：前者是普通函数、内部 `scope.launch` 落库；后者是 suspend、直接落库。应合并为单一 suspend 版本。

### 5.3 闹钟 requestCode 可能为负

`StartingAlarmScheduler.kt:114-115`：

```kotlin
fun requestCodeFor(completionId: String): Int = REQUEST_CODE_BASE + completionId.hashCode()
```

`String.hashCode()` 返回 `Int`，取值域含负数。实际风险极低（技术债清单判定可忽略），建议加 `and 0x7FFFFFFF` 兜底。

### 5.4 SettingsScreen 单函数过长

`SettingsScreen.kt` 主函数体 `:385-1126`，共 742 行，设置项定义与回调绑定全内联。建议改为配置驱动的列表形式。

---

## 六、设计讨论项（非缺陷）

### 6.1 专注「放弃」后的任务状态归属

**定性已修正（v2）**：v1 将其列为 P0 缺陷属定性错误。`abandon()` 的 KDoc 明写「放弃整场：当前专注轮 >=1 分钟按 ABANDONED 记账，**任务保持 IN_PROGRESS**」（`FocusTimerController.kt:142`）—— 这是**文档化的有意设计**，非遗漏。

同时 v1 称「用户无从退出」与事实不符：Today 进行中卡片区提供**完成、跳过、重进专注**三个出口（`TodayScreen.kt:326-328`），且 `onSkipTask` 明确放行 `IN_PROGRESS`（`TodayViewModel.kt:264`）。

**真正的产品决策点**：级联中 `computedEndAt` 对 `IN_PROGRESS` 取 `max(plannedEnd, now)`（`CascadeDelayCalculator.kt:110`），`now` 每分钟推进 → 长期滞留的进行中任务会持续推后下游。**该行为对普通「开始」路径同样存在**（用户点开始后走开即触发），并非 `abandon` 专属。

需产品定夺：进行中任务滞留时应如何处理？候选方案 ——（a）维持现状，由用户自行处理；（b）超过阈值自动转 SKIPPED；（c）`abandon` 时单独回退为 `PENDING`。**建议作为设计讨论项，在下次迭代规划时一并决策。**

---

## 七、建议执行顺序

| 优先级 | 事项 | 章节 | 工作量 |
|---|---|---|---|
| **P0** | 单进行中任务守门（需先定 A/B 方案） | §3.1 | 0.5h |
| **P1** | 归档口径统一 | §4.1 | 0.5h |
| **P1** | 撤销补重排闹钟 | §3.2 | 0.5h |
| **P1** | focus_sessions 进备份 | §4.3 | 半天 |
| **P2** | Canvas 缓存（整洁性，非瓶颈） | §4.2 | 2-3h |
| **P2** | FocusTimerController 补测试 | §5.1 | 半天 |
| **P2** | recordRound 去重 / requestCode 兜底 / SettingsScreen 拆分 | §5.2-5.4 | 合计半天 |
| 待定 | 进行中任务滞留策略 | §6.1 | 需产品决策 |

v1 曾将 3 项列为 P0。经复核后：§3.1 保留 P0；§3.2 降为 P1；§3.3 转为设计讨论项。**实际 P0 仅剩 1 项。**

---

## 八、审查范围与局限

**本次审查未执行**：构建、单元测试、真机 / 模拟器验证、性能 profiling。

因此：

- §4.2 的性能判断为静态分析，未经运行时 profiling
- 现有测试是否全部通过，本次未确认
- 所有结论基于源码阅读，不含运行时行为验证

**工作区状态**：审查时 `git status` 显示 `gradle.properties` 有未提交改动，提交前请确认内容。

**未纳入报告的内容**：与历史设计文档（PRD / 路线图 / 设计稿）的实现差异，理由见第一章。

---

## 九、正面确认

以下方面做得扎实，建议保持：

- **`CascadeDelayCalculator`**（`:11-29`）文档注释将 6 条规则、跨计划隔离、幂等性写得清楚，`scanChain` 抽为可单测纯函数
- **Room 迁移纪律**：v0.4.1 起全部显式声明，`WakePlanDatabase.kt:54-133` 维护完整 schema 版本史
- **时间可测性**：`Clock` 通过 Hilt 注入贯穿所有 domain 用例
- **加密设计**：SQLCipher 4.6.1 + AndroidKeyStore 密钥包裹方案完整（**本报告仅从代码结构确认方案存在且自洽，未验证运行时密文状态**）
- **Compose 规范**：状态收集统一使用 `collectAsStateWithLifecycle`
- **列表 key 正确**：LazyColumn 多段使用带前缀的 completion.id 防跨段冲突
- **i18n 彻底**：ui 目录硬编码文案零匹配

---

## 附录 A · v1 被推翻的结论

保留于此以便二次采信时判断哪些条目需要额外核实。

| v1 条目 | 问题 | v2 处置 |
|---|---|---|
| §3.3 abandon 不回收状态（列为 P0） | 漏读 `FocusTimerController.kt:142` KDoc 声明的设计意图；且「用户无从退出」与 `TodayScreen.kt:326-328` 三个出口矛盾；级联危害非 abandon 专属 | 转为 §6.1 设计讨论项 |
| §3.2「任务静默消失」 | 夸大。任务回 PENDING 仍显示，重进 Today 或完成任意任务即触发 refresh 自愈 | 下调为「本次会话内提醒缺失」 |
| §4.1 主卡「随机命中其一」 | 夸大。列表按时间排序，`firstOrNull` 取值确定 | 改为「可能非用户预期」 |
| §4.2「每帧 100 次测量」 | 夸大。`now` 每分钟变一次，横向滚动为平移不重绘 | 量级修正为「约每分钟一次」 |
| §4.2 `:921` 有 `.toList()` | 失实。实为 `for (h in 0..22 step 2)`（`:921`） | 删除该条 |
| §八「加密已验证 DB header 为密文」 | 越界。静态审查得不出运行时结论，且与第八章「未做真机验证」自相矛盾 | 改为「从代码结构确认方案存在」 |
| §3.1「不阻断操作」的建议 | 自相矛盾。不阻断即守不住不变量 | 改为 A/B 两方案待产品定调 |
| 规模数字（main 14958 / test 5358） | 统计口径为非空行，与物理行不符 | 统一为物理行：16241 / 6019 |
| §5.1「data 其他 23」 | 漏算三包（splash 7、domain/model 6、widget 6），表格加总 262 ≠ 296 | 改为完整分布表 |

> **口径纪律（v2 新增）**：报告中所有行数统一采用**物理行数**口径，避免与非空行数混用导致无法比对。

---

> 本报告为静态代码审查结论，供后续迭代参考。修复任何一项时，请同步补充对应单元测试。
