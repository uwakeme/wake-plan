# Wake Plan — 开发路线图 v0.1

| 项目 | 内容 |
|---|---|
| 关联 PRD | `docs/PRD.md` v0.1 草案 |
| 关联设计 | `design/` 原型（HTML + Design Token CSS） |
| 目标版本 | v0.1 MVP |
| 节奏 | 单人 / 不设死线 / 参考 8-10 周 |
| 平台 | Android API 26+ / 目标 API 35 / 单 module |
| 形态 | 本地优先 · 零外联 · 无后端无账号 |

---

## 1. PRD 5.3 开放问题最终决策

> 这 7 个决策在 2026-08-07 plan-mode 会话中拍板，作为 v0.1 实施基线。

| # | 开放问题 | 决策 | 实施影响 |
|---|---|---|---|
| 1 | 颜色预设 | **10 预设 + 自定义 hex** | 创建计划表单多一个色板行 + hex 输入框 |
| 2 | 通知默认提前 | **5 分钟** | DataStore `taskStartingLeadMinutes` 默认值 = 5 |
| 3 | 重复规则范围 | **全量 4 枚举** | `TimeSegment.repeatRule` ∈ {`ONCE`, `WEEKDAYS`, `DAILY`, `CUSTOM`}；`customDays: Set<DayOfWeek>` 仅 `CUSTOM` 时填 |
| 4 | 完成统计面板 | **v0.2 推迟** | v0.1 不实现 US-12，统计接口预留不接 |
| 5 | 本地化 | **纯中文 MVP** | strings 不抽到 `values/strings.xml`，UI 文字硬编码；v0.2 再 i18n |
| 6 | 自动延期撤销 | **v0.2 推迟** | v0.1 只做正向自动延期，Snackbar 不带 undo 按钮 |
| 7 | 备份加密 | **明文 + 可选加密** | Settings 加"加密备份"开关，勾选后要求 passphrase；导出 AES-256 + PBKDF2 |

---

## 2. 技术栈（最终）

| 类别 | 选型 | 版本 | 备注 |
|---|---|---|---|
| 语言 | Kotlin | 2.0.21 | |
| UI | Jetpack Compose | BOM 2024.10.00 | 含 Material 3 |
| 状态 | StateFlow + `collectAsStateWithLifecycle` | | UDF |
| 异步 | Coroutines + Flow | 1.8.x | |
| DI | Hilt | 2.52 | KSP 编译期 |
| 注解处理 | KSP | 2.0.21-1.0.27 | |
| 导航 | Navigation Compose | 2.8.x | |
| DB | Room + SQLCipher | Room 2.6.1 / SQLCipher 4.5.4 | AES-256 加密本地数据库 |
| 偏好 | DataStore Preferences | 1.1.1 | |
| 序列化 | kotlinx.serialization | 1.7.3 | JSON 备份导出 |
| 时间 | java.time (desugaring) | coreLibraryDesugaring 2.1.2 | API 26+ 兼容 |
| 测试 | JUnit5 + Turbine + Room Testing + Compose UI Test | | |
| 构建 | Gradle | 8.7 | wrapper 走阿里云 macports 镜像 |

**国内镜像（用户偏好）**：
- `settings.gradle.kts` 配阿里云 maven 三件套：`public` / `google` / `gradle-plugin`
- `gradle-wrapper.properties` `distributionUrl` 走 `mirrors.aliyun.com/macports/distfiles/gradle/gradle-8.7-bin.zip`

---

## 3. 架构

### 3.1 Module 结构

单 module（`:app`）起步。包结构按 Clean 3 层切分：

```
app/src/main/java/com/wakeplan/app/
├── App.kt                    # @HiltAndroidApp
├── MainActivity.kt           # @AndroidEntryPoint
├── ui/
│   ├── theme/                # Color / Type / Shape / Theme
│   ├── nav/                  # NavHost + BottomTabBar
│   ├── today/                # 当前时刻屏
│   ├── plan/                 # 计划列表 / 详情 / 新建
│   ├── task/                 # 任务编辑
│   ├── template/             # 模板库
│   ├── settings/             # 设置
│   └── common/               # 通用 Composable（卡片 / 按钮 / Snackbar / 冲突面板）
├── domain/
│   ├── model/                # Domain Model（纯 Kotlin data class）
│   ├── repository/           # Repository 接口
│   └── usecase/              # AutoExtendCalculator / ConflictDetector
├── data/
│   ├── db/                   # Room entities + DAOs + Database
│   ├── prefs/                # DataStore
│   ├── repository/           # Repository 实现
│   └── backup/               # JSON 备份导出/导入 + 加密
└── di/                       # Hilt Modules
```

### 3.2 数据流

```
UI (Compose)
  ↕ collectAsStateWithLifecycle
ViewModel (StateFlow)
  ↕ suspend / Flow
UseCase
  ↕
Repository（接口在 domain/，实现在 data/）
  ↕
Room DAO / DataStore
```

---

## 4. 数据模型

直接照搬 PRD 4.2，**entity 定义不另作改动**：

- `Plan`（id / title / colorHex / startDate / isActive / createdAt / updatedAt）
- `TimeSegment`（id / planId / title / startMinutes / endMinutes / repeatRule / customDays）
- `Task`（id / segmentId / title / plannedDurationMinutes / autoExtend / isLocked / orderInSegment / note）
- `PlanTemplate`（id / title / colorHex / isBuiltIn / segments）
- `TaskCompletion`（id / taskId / planId / scheduledDate / plannedStart / plannedEnd / actualStart / actualEnd / status / overrunMinutes / affectedTasks）

**关键约定**：
- `TimeSegment` 跨午夜段：写入时 `endMinutes` 可 > 1440，按 `dayOffset` 处理（运行时 `TimeWindowCalculator` 折算）
- `Task` 与 `TaskCompletion` 关系：每条 `Task` 在 `scheduledDate` 当天生成一条 `TaskCompletion`（PENDING）；开始 → IN_PROGRESS；完成 → COMPLETED + 写 overrun
- `TaskCompletion.affectedTasks`：本次自动延期受影响的下游任务 id 列表，便于追溯

---

## 5. 路线图（分 Phase）

| Phase | 范围 | 预计 | 状态 |
|---|---|---|---|
| **0. 骨架** | Gradle 工程 + 主题 + 3 tab 导航 + Hilt + Room 空壳 + AGENTS.md + 推 GitHub | 1-2 轮 | ✅ 完成 |
| 1. CRUD | 计划/段/任务/模板基础 CRUD（含 CUSTOM 重复规则 UI + 10 色 + hex 自定义） | 1-2 周 | ✅ 完成 |
| 2. 核心交互 | 开始/完成 + 自动延期算法 + 冲突检测内联 + 当前时刻聚合视图 | 2-3 周 | ✅ 完成 |
| 3. 通知 | WorkManager + AlarmManager + 4 渠道（starting/overdue/completed/recap） + 权限引导 | 1 周 | ✅ 完成 |
| 4. 备份/收尾 | JSON 备份导出导入（明文 + 可选 AES-256）+ Settings 完整化 | 1 周 | ✅ 完成 |
| **v0.2 候选** | 时间线预览 (US-08) · 统计面板 (US-12) · 自动延期撤销 · i18n · 模板库打磨 | 4-6 周 | 候选 |
| **v0.3 候选** | AppWidget + Quick Settings Tile | 3-4 周 | 候选 |
| **v1.0 候选** | 隐私政策 + 应用市场上架 + E2E 测试 | 4 周 | 候选 |

---

## 6. Phase 0 当前轮执行清单

### 6.1 仓库元文件

- [x] PRD + 原型 review
- [x] `docs/plan/00-roadmap.md`（本文件）
- [x] `AGENTS.md`（项目级 agent 协作约定：构建命令 / 模块布局 / 命名约定 / 提交粒度）
- [x] `README.md`（中文版，PRD 摘要 + 路线图摘要 + 当前进度）
- [x] `.gitignore` 补充 Android 特定条目（`/build/`, `/.idea/`, `local.properties` 等）
- [x] 推 GitHub `git@github.com:uwakeme/wake-plan.git`（SSH）

### 6.2 Gradle 工程骨架

- [x] `settings.gradle.kts`（阿里云 maven 镜像三件套 + version catalog）
- [x] `gradle/libs.versions.toml`（版本目录）
- [x] `gradle/wrapper/gradle-wrapper.properties`（阿里云 macports 路径）
- [x] `gradle/wrapper/gradle-wrapper.jar`（如缺失）
- [x] `gradlew` / `gradlew.bat`（wrapper 脚本）
- [x] `gradle.properties`（JVM args + AndroidX 开关 + KSP incremental）
- [x] `build.gradle.kts`（root，plugin 声明）
- [x] `app/build.gradle.kts`（com.android.application + kotlin + hilt + ksp + room + compose + sqlcipher）
- [x] `app/proguard-rules.pro`

### 6.3 Android 工程基础

- [x] `app/src/main/AndroidManifest.xml`（Application + MainActivity + 必要权限：POST_NOTIFICATIONS / SCHEDULE_EXACT_ALARM / RECEIVE_BOOT_COMPLETED）
- [x] `app/src/main/res/values/strings.xml`（仅 app_name）
- [x] `app/src/main/res/values/themes.xml`（splash 主题）
- [x] `app/src/main/res/values/colors.xml`（备用）
- [x] `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` + 前景背景资源（占位 launcher）

### 6.4 Kotlin 源码骨架

- [x] `App.kt`（`@HiltAndroidApp`）
- [x] `MainActivity.kt`（`@AndroidEntryPoint` + `setContent { WakePlanTheme { AppNavHost() } }`）
- [x] `ui/theme/Color.kt`（映射 `design/colors_and_type.css` 的 light/dark token）
- [x] `ui/theme/Type.kt`（DM Sans + Noto Sans SC + JetBrains Mono，中文走系统 Noto Sans SC）
- [x] `ui/theme/Shape.kt`（radius token 映射）
- [x] `ui/theme/Theme.kt`（`WakePlanTheme` Composable）
- [x] `ui/nav/AppNavHost.kt`（NavHost + 3 路由：today / plans / settings）
- [x] `ui/nav/BottomTabBar.kt`（对应 `design/partials/project-shell.html` 的 3 tab）
- [x] `ui/today/TodayScreen.kt`（占位文案）
- [x] `ui/plan/PlanListScreen.kt`（占位文案）
- [x] `ui/settings/SettingsScreen.kt`（占位文案）
- [x] `data/db/WakePlanDatabase.kt`（空 Room database，5 entity 已声明，DAO 暂空接口）
- [x] `data/db/entity/PlanEntity.kt`（照搬 PRD 4.2）
- [x] `data/db/entity/TimeSegmentEntity.kt`（含 `RepeatRule` enum + `customDays: Set<DayOfWeek>`）
- [x] `data/db/entity/TaskEntity.kt`
- [x] `data/db/entity/PlanTemplateEntity.kt`
- [x] `data/db/entity/TaskCompletionEntity.kt`
- [x] `data/db/converter/Converters.kt`（`LocalDate` / `Instant` / `Set<DayOfWeek>`）
- [x] `di/DatabaseModule.kt`（Hilt 提供 Database）
- [x] `di/RepositoryModule.kt`（占位）

### 6.5 验收标准

- [x] `gradle wrapper` 配阿里云镜像，本地能下到 `gradle-8.7-bin.zip`（macports 路径）
- [x] `./gradlew :app:assembleDebug` 编译通过
- [ ] 安装到模拟器/真机，3 tab 能切，浅深主题切换正常 (Phase 1 启动前补，AVD 验证)
- [x] Room Database 启动无 crash（Hilt 注入链路通）
- [x] 推 GitHub `git@github.com:uwakeme/wake-plan.git`，主分支 `main` 可访问

---

## 7. v0.2 候选清单（不展开）

- 时间线预览 (US-08)：水平 24h 时间轴 + 段背景 + 任务色块 + 滚动切换日期
- 模板库 UX 打磨：从模板新建计划的引导
- 完成统计面板 (US-12)：7/30 天完成率折线 + 按计划堆叠柱状 + 平均延期
- 自动延期撤销：Snackbar undo 按钮 + DataStore undo token
- i18n：抽 `values/strings.xml` + `values-en/`
- 桌面小部件 (AppWidget)
- Quick Settings Tile

---

## 8. 风险与缓解

继承 PRD 5.2：

| 风险 | 缓解 |
|---|---|
| 厂商杀后台闹钟不响 | WorkManager + AlarmManager 双触发 + 引导用户加白名单 + "测试通知"按钮 |
| Android 13+ 通知权限被拒 | 启动引导页必申请 + 拒绝后设置页红字 + 系统设置深链 |
| 自动延期边界 case | 单元测试 ≥ 90% 覆盖 + "撤销最后一次"（v0.2）+ 实时日志 |
| Room 数据库迁移 | 严格版本号 + `MigrationTestHelper` 自动化 |
| 跨午夜段计算 bug | 单独抽 `TimeWindowCalculator` + 段编辑可视化预览 |
| 备份导出跨设备兼容 | ISO-8601 + java.time + 备份 JSON 嵌 schemaVersion |

---

> **当前轮结束条件**：Phase 0 全部完成 + GitHub 远端可访问 + 模拟器能开主屏 + AGENTS.md / README.md 就绪。

---

## 9. v0.1 当前进度（2026-08-08 更新）

**已完成**：Phase 0 / 1 / 2 / 3 / 4 全部推送到 `main`，177 个 JVM 单测全过。

| Phase | commits | 测试数 | 关键产出 |
|---|---|---|---|
| 0 | `0~9` | 0 | Gradle 8.7 + Compose BOM 2024.10 + Hilt 2.52 + Room 2.6.1 骨架；阿里云 maven 镜像 |
| 1 | `a~20+` | 0 | 5 entity + Repository + 5 Screen + 10 色 + CUSTOM 重复规则 |
| 2 | `a7e9d31 / d92c289 / 228a167 / 335e3a6 / 73dce96` | 51 | TimeWindow + AutoExtend + ConflictDetector + TodayScreen 24h 视图 |
| 3 | `757c0f0 / fce73f8 / 213e0da` | 31 | DataStore 偏好 + 4 渠道 + Builder + AlarmManager + WorkManager + Onboarding + Settings 通知配置 + deep link |
| 4 | `de4bba7 / 99273fb / 89d1242 / b00d597` | 87 | BackupEnvelope + 5 DTO + AES-256-GCM + PBKDF2-SHA256 600k + BackupImporter (完全覆盖) + BackupManager + BackupViewModel + Passphrase/Confirm dialogs + SAF |

**SQLCipher 状态**：4.5.x 4KB page size 不支持 Android 15+ 16KB 要求；阿里云 4.6.1 AAR 损坏（Maven Central HTTPS 443 被封无法同步）。v0.1 暂用 Room 默认 SQLite，备份加密走应用层 AES-256 + PBKDF2（PRD 5.3 #7）。

**v0.1 状态**：所有 5 个 Phase 完成，177 个测试全过，编译通过。准备 v0.1 release（清理 demo seed / 调试 log / BuildConfig guard，AGP warning，签名配置）。

**v0.2 候选**：时间线预览 (US-08) · 完成统计 (US-12) · 自动延期撤销 · i18n · 模板库打磨 · SQLCipher 重新评估 · AppWidget · Quick Settings Tile。

