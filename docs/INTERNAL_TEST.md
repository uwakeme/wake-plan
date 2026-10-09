# Wake Plan v0.5 内测说明

> 2026-08-11 起的保守型内测。v0.3.0 是稳定性 + 隐私 + 国际化补丁；v0.3.1 砍掉
> time_segments 表扁平化；v0.4.0 是功能补齐版；v0.4.1 是正确性修复版；
> **v0.5.0 是路线图存量收口版**：每日回顾通知 / 按月统计 / CSV 导出 / 撤销队列 /
> 数据保留策略，外加组件去重。
> 现阶段只对熟人小范围，不上应用市场。

## 安装

从 GitHub Release 页面下载最新版 release APK：

- v0.4.0（当前）：<https://github.com/uwakeme/wake-plan/releases/tag/v0.4.0>
- v0.3.1：<https://github.com/uwakeme/wake-plan/releases/tag/v0.3.1>
- v0.3.0：<https://github.com/uwakeme/wake-plan/releases/tag/v0.3.0>

下载链接：`app-release.apk`（约 40 MB，SQLCipher 4.6.1 native 库占大头）

Android 8.0+ 即可装。第一次装会被系统提示"未知来源"，到设置里允许一次就行。

> ⚠️ APK 用的是 **debug keystore 临时签**（不是正式 release key），
> 跟 Android Studio 跑出来的 debug APK 用的同一个 key。
> 正式上架前会换 sign。卸载重装测试时如果遇到 "已安装的版本签名不同"，
> 卸了重装即可。

## 数据

- 所有数据存本机（SQLCipher AES-256 加密）
- 不发任何网络请求
- 备份走 SAF（系统文件选择器），由用户主动控制位置
- 卸载 App = 数据清空（没有云端备份）

跨版本迁移（v0.4.1 修复迁移链路）：
- 从 v0.2 装上来的 App（schema v1）：v1→v2（plans 加 archived）→ v3（扁平化重建
  tasks，任务/完成记录清零，计划与模板保留）→ v4（tasks 加创建日）。全程显式迁移，
  不再整库清零（v0.4.0 及之前对 v1/v2 用户是 destructive 清库）
- 从 v0.4.0（schema v3）升级：只走 v3→v4，数据全保留
- 降级安装（debug 场景）允许清库重建

## 测什么

按"日常用得最多 → 关键功能 → 边角"这个顺序：

### v0.5 新增要测
1. **每日回顾**：设置 → 通知区打开"每日计划回顾" → 建几个任务并完成一部分 → 等 21:00（或改设备时间到 20:59 再等一分钟）→ 应收到"今天完成 X/Y 个任务（Z%）"通知；点通知跳 Today；当天没有任何任务时不推送
2. **按月统计**：Stats tab 切"本月" → 窗口应从本月 1 号到今天；"近7天/近30天"仍是滚动窗口
3. **CSV 导出**：设置 → 备份区"导出任务记录（CSV）" → 选保存位置 → 用 Excel 打开中文不乱码（UTF-8 BOM）；删除过的计划/任务在 CSV 里显示为 id
4. **撤销队列**：Today 连续点多个开始/跳过/完成 → Snackbar 一条接一条出（不再互相顶掉），最多记 5 条，逐条可撤销
5. **数据保留**：设置底部"数据保留" → 默认"永久保留"；切"保留 90 天"后重启 App → 90 天前已完成/已跳过的记录被清理，未开始和进行中的不受影响（建议先用 CSV 备份再试）

### v0.4.1 新增要测（正确性修复，重点）
1. **链式延期真正生效**：同计划建 A(9:00-9:30)、B(9:30-10:00) → 开始 A → 拖到 9:45 再点完成 → B 应变 9:45-10:15 并**持久化**（杀掉重开 App 不弹回原时间），B 的开始提醒也按新时间
2. **跨计划不传染**：A 计划任务超时不再推 B 计划的任务（对齐 PRD）
3. **临时任务只今天**：Today 快速录入的任务明天不再出现（ONCE 语义修复；v0.4.1 前建的旧 ONCE 任务保持每天行为）
4. **跨午夜**：建 23:00-01:00 的任务 → 再建一个 00:00-00:30 的任务 → 两者冲突应能标出；跨午夜任务拖到凌晨未完成会报超时
5. **跨天**：Today 页挂机过午夜后自动翻到新一天并刷新（不再显示昨天）
6. **通知开关即时生效**：关掉"任务开始提醒"或改提前分钟数 → 已挂闹钟立刻按新配置（v0.4.1 前关了还响）
7. **App 自更新后闹钟不丢**：覆盖安装新版本 → 不打开 App，到点提醒照响（新增 MY_PACKAGE_REPLACED）
8. **撤销完成**：完成一个带延期的任务后 5 秒内撤销 → 下游任务弹回原时间
9. **英文完整**：切 English 过 Today/引导页/通知——v0.4.1 清掉了绕过资源的硬编码中文

### v0.4 新增要测
1. **重启闹钟**：建一个 10 分钟后开始的任务 → 杀掉 App → 重启手机 → 不打开 App 等提醒，到点应该响
2. **模板应用**：设置 → 模板库 → 任一模板详情应显示任务列表 → "应用为计划" → 新计划里任务齐全；失败会弹 Snackbar（不再静默）
3. **快速录入**：Today 页右下 + → 选计划填标题选时间 → 添加 → 时间线上立刻出现且可打卡
4. **Widget 实时**：桌面加小组件 → 回 App 打卡一个任务 → 桌面小组件几秒内变化；跨午夜后 widget 显示新一天
5. **归档**：计划详情菜单归档 → 回列表看底部"已归档"折叠区 → Today 不再显示该计划任务 → 取消归档恢复
6. **冲突标注**：故意建两个时间重叠的任务 → 计划详情里两行都应显示"今日重叠 N 分钟"
7. **导入回滚**（可选测）：导入一个中途损坏的备份文件 → App 数据应保持原样
8. **深色主题**：系统深色模式下全页面过一遍（v0.4 起强制深色）

### 必须测（主流程）
1. **新建计划 + 任务（v0.3 扁平化）**：Plans tab → 右下 + → 填标题选色 → 进 plan 详情 → 加任务（标题 + 起止时间 TimePicker + 重复规则 chips + 可选锁定 / 备注）→ 保存
2. **今日时间线**：Today tab 翻日期（← / 今天 / →），确认 task 在正确时间显示
3. **开始 + 完成 + 链式碰撞延期**：在 Today 屏上点任务「开始」，等段时间点「完成」或拖出时间轴，确认后续任务按 `CascadeDelayCalculator` 链式顺延；isLocked 任务不会被推走也不让别人撞过来
4. **撤销自动延期**：点完完成后 5 秒内 Snackbar 出现「撤销」按钮，点一下确认撤回
5. **冲突检测**：建两个时间重叠的任务（v0.3 跨 plan 也会冲突），确认 plan 详情有红色冲突提示
6. **通知**：到 Settings 屏把 lead time 改成 5 分钟，建个 5 分钟内的任务，看通知能不能弹

### 应该测（v0.2 / v0.3 新功能）
7. **完成统计**：今日 tab 完成几个任务后切到 Stats tab，看 7 天 / 30 天数字 + 折线图 + 按计划柱状图是否对得上
8. **模板库**：Settings 屏点「模板库」→ 3 个内置模板 → 进详情看任务列表（v0.3 不含段）→ 点「应用此模板」回 Plans（v0.3 只建空 plan，task 复制留后续版本）
9. **桌面 AppWidget**：长按桌面 → 小部件 → 找 Wake Plan → 拖到桌面，看 mini timeline 是不是更新
10. **快捷设置 Tile**：下拉系统快捷栏 → 编辑 → 拖 Wake Plan tile 进去 → 点它是不是跳到 Today tab
11. **SQLCipher 加密**：装上后看 `/data/data/com.wakeplan.app/databases/wakeplan.db` 前 16 字节应该是随机的密文（不是 `SQLite format 3\0`）。备份导出后用文本编辑器看是 JSON，删 App 重装再用「导入备份」恢复
12. **设置 - 关于**：进 Settings 滚到底，「关于」section 看隐私政策 dialog 弹出来 + 跳 GitHub repo
13. **设置 - 语言切换**：进 Settings 切到「App 语言」→ 中文 / English / 跟随系统三选一，确认 UI 即时切

### 可选测（边角 + 容错）
14. **备份 - 导出明文 / 加密**：明文导出后用文本编辑器打开看是 JSON；加密导出要至少 8 位密码，导完再导入
15. **i18n 完整**：切成 English 后看所有屏（Today / Plans / Plan 详情 / 任务编辑 / Template 详情 / Template 列表 / Settings / 备份 dialog）有没有漏翻
16. **横竖屏旋转**：竖屏建好计划 → 转横屏看 UI 有没有崩
17. **后台杀进程**：计划建好 → 长按后台杀掉 App → 重新打开看任务还在 + 通知有没有漏
18. **切日期（Yesterday / Tomorrow）**：Today tab 切到明天建个任务 → 切回来确认任务不在今日时间线
19. **跨午夜任务**：建一个 23:00 - 01:00 的任务（endMinutes = 25*60），确认 TaskEditor 提示"结束跨过次日 00:00（持续 120 分钟）"，时间线正确显示
20. **自定义星期重复**：建一个 CUSTOM 重复规则的任务，勾选周一三五；今天周日时不展开 PENDING（confirm today tab 无任务），切到下周一确认出现

## 已知问题 / 不在 v0.5 范围

- **多语言只有中英** — 其他语言先按系统默认（=英文）
- **每日回顾触发时间** — WorkManager 24h 周期对齐 21:00，长期可能有小幅漂移（分钟级），不保证秒级准时
- **超时提醒节奏** — WorkManager 周期 15 分钟是系统硬下限（v0.4.1 前声明的 10 分钟实际也是 ≥15 分钟）；"每 10 分钟一档、最多 3 次"的封顶语义由检查逻辑按超时时长推导
- **CSV 全量导出** — 暂不分页（与备份导出同假设，数据量 1k 行级；数据保留策略上线后自然有界）
- **没有番茄钟 / 专注模式** — v0.6（绑任务的专注页，设计已拍板）
- **撤销栈** — 深度 5 的 Snackbar 队列；跨天的操作不建议依赖撤销
- **精确闹钟权限** — 只保留 SCHEDULE_EXACT_ALARM；未授权时降级 inexact 闹钟（Doze 下可能延迟）

## v0.5.0 changelog

**v0.4.1 → v0.5.0 改了什么**（计划详见 `docs/plan/18-v0.5-v0.6-feature-plan.md`）：

- **每日回顾（PRD US-10 收尾）**：每日 21:00 推送当日完成率小结；分母用任务定义推导
  口径（没开 App 的日子不稀释）；当天无任务不推送；点击跳 Today；设置页恢复开关
- **按月统计**：Stats 切换加"本月"（自然月首→今天）；"近7天/近30天"文案与语义对齐
- **CSV 导出**：设置 → 备份区新增入口，全量任务执行记录导出（UTF-8 BOM，Excel
  中文不乱码；RFC 4180 转义；已删计划/任务回退 id）
- **撤销队列**：单槽改深度 5 的 FIFO——上一条没消费不再被顶掉，Snackbar 逐条出队
- **数据保留**：设置页"数据保留"三选一（默认永久；90 天/1 年清理终态超龄记录，
  进行中/未开始永不动）
- **整备**：TimePickerDialog 与时间格式化组件去重（显示行为不变）

**v0.5.0 验收**：291 个 JVM 单测全过（v0.4.1 为 264），lint 0 error，
`assembleDebug` 干净，装机冒烟通过。

## v0.4.1 changelog

- **多语言只有中英** — 其他语言先按系统默认（=英文）
- **超时提醒节奏** — WorkManager 周期 15 分钟是系统硬下限（v0.4.1 前声明的 10 分钟实际也是 ≥15 分钟）；"每 10 分钟一档、最多 3 次"的封顶语义由检查逻辑按超时时长推导
- **没有番茄钟 / 专注模式** — v0.6（绑任务的专注页，设计已拍板）
- **没有 CSV 导出 / 按月统计 / 每日回顾通知** — v0.5（路线图存量）
- **撤销栈** — 现在只有最后一步的 5 秒 Snackbar 撤销，栈版留 v0.5
- **精确闹钟权限** — v0.4.1 移除了 USE_EXACT_ALARM（Play 政策仅限闹钟/日历类），只保留 SCHEDULE_EXACT_ALARM；未授权时降级 inexact 闹钟（Doze 下可能延迟）

## 怎么报 bug

直接在 GitHub repo 提 Issue：<https://github.com/uwakeme/wake-plan/issues/new>

Issue 模板自带「Bug report」选项。要写清楚：

1. **重现步骤** — 我照着做能复现
2. **期望行为** vs **实际行为**
3. **设备 + Android 版本** — 比如 "Pixel 7 / Android 14" 或 "模拟器 API 33"
4. **App 版本** — Settings → 关于里看
5. **截图 / logcat** — 截图最有用，logcat 加分（崩溃一定要带 logcat）

崩溃 logcat 怎么拿：
```bash
adb logcat -d -t 500 AndroidRuntime:E *:S
```

## 反馈节奏

- **崩溃 / 阻塞主流程** — 当天看，当天回
- **一般 bug** — 一周内 review
- **UX 建议** — v0.3 内测结束统一 review

## v0.4.1 changelog

**v0.4.0 → v0.4.1 改了什么**（正确性修复版，263 个 JVM 单测全过）：

- **链式延期真正生效并落库（核心修复）**：v0.4 之前"完成超时任务推后下游"只在界面内存里算——
  生产数据流中 overrun 从未传导（完成即终态被跳过），下游闹钟/统计也永远按原时间。
  现在 COMPLETED 按 actualEnd 推同计划下游任务并持久化，重排下游提醒（PRD AC-06）
- **跨计划不传染**：A 计划的延期不再传播到 B 计划（对齐 PRD AC-06.5；v0.3 是混排互推）
- **ONCE 任务修复**：只在建任务那天生效。快速录入的"临时任务"明天不再复活
  （schema v4：tasks 加 created_epoch_day；v0.4.1 前建的旧 ONCE 任务保持每天行为）
- **Room 迁移链路修复**：DatabaseModule 接入显式迁移（1→2→3→4）并移除
  fallbackToDestructiveMigration（升级不再静默清库；降级仍允许重建）；
  androidTest 迁移测试重写并挂上 schema assets（v0.3.1 起该测试引用已删除的符号无法编译）
- **跨午夜 / 跨天修复**：冲突检测与超时检查覆盖"昨天延伸到今天"的跨午夜尾巴；
  Today / PlanList / PlanDetail / Widget 的"今天"跟随时间翻页，过午夜自动刷新
  （之前 VM 构造时固化日期，挂机过夜后一直显示昨天）
- **通知调度修复**：关闭"任务开始提醒"或改提前分钟数后已挂闹钟立刻按新配置
  （cancelAll 从空实现改为真取消）；App 自更新后闹钟重排（新增 MY_PACKAGE_REPLACED）；
  超时提醒最多 3 次封顶（之前每次都报"(1/3)"）；overdue 周期改为诚实的 15 分钟
- **统计口径修正**：完成率分母改为按任务定义推导——某天没打开 App 不再把完成率稀释成 0%；
  perDay 与全局"平均延期"统一为只对超时的完成求均值
- **数据健壮性**：补实例改 insertIfAbsent + 事务（并发刷新不再换行 id 丢提醒）；
  任务改时间/改重复规则后当天未开始实例同步或清理；备份导出事务快照；
  导入迭代次数上限 + 64MB 文件上限（防恶意文件 DoS/OOM）；DB 密钥损坏时
  恢复而不是崩溃循环；内置模板只 seed 一次（删光后不再复活）
- **深链修复**：MainActivity singleTop + 点通知自动切到 Today 并按扁平列表真实索引
  滚动定位（v0.4 后旧索引算法基本滚不到位）；冲突标注按任务匹配且对方计划名显示正确
- **i18n 清零**：Today 页问候语/星期/时长、Onboarding 全部文案、通知文案、widget 日期、
  计划徽章全部抽到 strings（v0.4 之前英文用户这些位置整屏中文）
- **UX 补漏**：模板应用失败弹 Snackbar；统计切区间有加载态；引导页支持返回；
  新建计划可选开始日期（DatePicker 实装）；任务编辑器选结束 ≤ 开始自动按跨午夜处理
  （不再静默截断丢数据）；切语言不再重播品牌启动动画
- **其他**：USE_EXACT_ALARM 移除（Play 政策）；StartingAlarmReceiver 改 exported=false；
  GitHub Actions CI（单测 + lint + 双 APK 编译）；`app/release/` 移出 git 跟踪

## v0.4.0 changelog

**v0.3.1 → v0.4.0 改了什么**（计划详见 `docs/plan/16-v0.4-feature-plan.md`）：

- **重启后提醒不再丢**：v0.3 手机一重启所有 starting 闹钟全丢；现在 BootReceiver 收到
  BOOT_COMPLETED / 时间或时区变更后自动重排（调度链抽成 ScheduleRefresher，与打开 App 共用）
- **模板"应用为计划"真复制任务了**：v0.3 只建空 plan；v0.4 三个内置模板（标准晨练 /
  标准学习 / 周末阅读）自带任务 spec，应用时和 plan 同事务复制成真任务，预览页也能看到任务
- **备份导入加了 Room 事务**：中途失败整体回滚，不会再出现"旧数据清了新数据只进一半"
- **快速录入**：Today 页右下角 +，弹层里选计划、填标题、选起止时间一键加任务，
  自动铺当天实例并重排闹钟（不用再进 3-4 层编辑器）
- **Widget 实时刷新**：打卡 / 延期后桌面小组件即时更新，不再等 30 分钟或打开 App
- **冲突可视化（PlanDetail）**：计划详情里时间窗重叠的任务行内标注"今日重叠 N 分钟"，
  不弹弹窗（Today 页原本就有）
- **计划归档**：计划详情菜单"归档"，归档计划收进列表底部"已归档"折叠区，
  不再进 Today 时间线、不再铺实例，历史统计保留；备份文件带上归档状态
- **完成确认通知**（可选）：设置页"任务完成确认"开关实装，打卡完成推低优先级通知
  （超时会带"超时 N 分钟"）；"每日计划回顾"开关先隐藏（v0.5 实装）
- **v0.4 视觉刷新**：强制深色主题，全页面改版（本次内测一起验）

**v0.4.0 验收**：256 个 JVM 单测全过（v0.3.1 是 219），lint 0 error，assembleDebug 干净

## v0.3.1 changelog

**v0.3.0 → v0.3.1 改了什么（v0.3 扁平化重构）**：

- **砍 time_segments 表，task 自带起止时间 + 重复规则**（schema v2 → v3，alpha 用户数据 reset，详见 `docs/plan/15-v0.3-flat-task-model.md`）
- **数据模型扁平化**：Plan → TimeSegment → Task → TaskCompletion 三层砍成 Plan → Task → TaskCompletion 两层，segment 实体彻底删
- **TaskEditor 完整重写**：起止时间 TimePicker（跨午夜 endMinutes > 1440 自动识别 + 提示）+ 重复规则（ONCE / WEEKDAYS / DAILY / CUSTOM 四选一 chips）+ 自定义星期复选 + 锁定 Switch + 备注
- **PlanDetail 砍段嵌套**：直接列任务（无段分组），任务行内显示时间 + 重复规则 + 锁定徽章
- **TodayScreen 时间线跨 plan 混排**：24h Canvas 画所有 plan 的行（同 plan 同色），now 标红线，冲突标红块
- **CascadeDelayCalculator 链式碰撞延期** 替换原 AutoExtendCalculator：isLocked 真正"钉死断链"（不再传 offset），COMPLETED/SKIPPED 不影响下游，跨 plan 当天链式
- **Template 简化**：v0.3 不实装"应用模板复制 tasks"，应用按钮只创建空 plan；TemplateDetail 加任务列表只读预览
- **i18n 修 22 个 `${...}` 字符串占位符**：v0.3.0 偷懒的 Kotlin 模板语法全换 `%1$s/%1$d`
- **删 SegmentEditorScreen / SegmentEditorViewModel 两个文件**（v0.3 不再需要）
- **AppNavHost 路由表简化**：去掉 `plans/{planId}/segments/...` 子路由

**v0.3.1 验收**：219 个 JVM 单测全过，assembleDebug 干净，APK 39.7 MB

**v0.3.0 base**（v0.3.1 没改的）：
- 隐私政策 + About section / Per-app locale / i18n 收尾 / Stats 空数据修复

## v0.3.0 changelog

完整 changelog 见 `docs/plan/00-roadmap.md` 第 11 节。简要：

**v0.2 → v0.3 改了什么**：
- **隐私政策 + 关于 section**（设置页底部）：4 条 bullet 讲清楚 App 不发请求、不收集数据、本地加密、无第三方 SDK
- **Per-app locale**（设置页「语言」section）：跟随系统 / 中文 / English 三选一，API 33+ 走系统 `LocaleManager`，低版本走 appcompat 1.7.0
- **Room Migration 测试**（androidTest）：v0.2 → v0.3 schema 升 v1 → v2（plans 表加 archived 字段），Migration 走 SQLCipher 加密路径
- **i18n 收尾**（11 个 UI 文件 / 187 个新 string key）：v0.2 留的硬编码中文全清，切英文时所有 UI chrome 都翻
- **Stats 屏空数据修复**：v0.2.5.2 留的 `maxOf` 空集合 crash bug

**v0.2 base**（v0.3 没改的）：
- 自动延期撤销 / 完成统计 / 时间线日期切换 / 模板预览+新建 / AppWidget / Quick Settings Tile / SQLCipher 4.6.1
- 235 个 JVM 单测全过
