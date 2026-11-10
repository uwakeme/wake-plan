<p align="center">
  <img src="./designs/banner/wake-plan-banner.png" alt="Wake Plan — 本地优先的多计划时间管理" width="100%">
</p>

# Wake Plan

本地优先的多计划时间管理 Android App。一个人同时跑几件事，健身、学习、副业，用"时间段 + 任务 + 自动延期"模型，让一天乱了的时候不用一路手动改时间。

## 它解决什么

市面上日程类 App 大多只服务一种场景（待办、日历、习惯），或者把"计划"和"日程"混在一起。一旦某项任务超时，连锁反应全靠用户手动拖，到一天结束计划基本形同虚设。

Wake Plan 想做的就一件事：让你一天打开一次 App，看到现在该做什么；遇到卡顿一键延期，不用挨个改时间。

零外联（App 不发任何网络请求，统计页也是本地算），本地加密（SQLCipher 4.6.1 AES-256，本机数据库不上云）。

## 路线图

| 阶段 | 状态 | 内容 |
|---|---|---|
| v0.1 MVP | 完成 | 计划 CRUD · 时间段 · 任务绑定 · 模板（基础）· 开始/完成 · 自动延期 · 当前时刻视图 · 冲突提示（内联）· 本地通知（任务开始+超时）· JSON 备份导出 |
| v0.2 | 完成（2026-08-11） | 自动延期撤销 · 完成统计数字 + Canvas 图表 · 时间线日期切换 · 模板预览 + 一键新建 · i18n（中英双语）· 桌面 AppWidget · 快捷设置 Tile · SQLCipher 4.6.1 加密本地 DB |
| v0.3 | 完成（2026-08-11） | 设置页「关于」section（隐私政策 + 版本 + 源代码）· Per-app locale 切换（跟随系统 / 中文 / English）· Room Migration 测试基础设施（v1→v2 走通加密路径）|
| v1.0 | 候选 | 计划归档 · 应用市场上架 · E2E 测试 |

完整计划在 `docs/plan/00-roadmap.md`。

## v0.2 新增能力

撤销最近一次延期（Snackbar 5 秒窗口）。Stats tab 加了 7/30 天完成数折线、按计划柱状、平均延期。今天的时间线可以翻日期了，←/今天/→ 按钮，"今日"标签会跟着切换。

模板现在是真东西：详情页展示 段+任务 树，"应用此模板"直接生成 plan。i18n 走通了，中英双语，199 个 key 一一对应。桌面 AppWidget 是 4×2 mini timeline，PendingIntent 一键跳回 Today。下拉面板的快捷设置 Tile 也是同样入口。

DB 从明文换成了 SQLCipher 4.6.1 加密，32 字节随机 passphrase，AndroidKeyStore master key 包装后存 DataStore。

## v0.3 新增能力

设置页底部多了「关于」section：隐私政策（4 条 bullet 讲清楚这 App 不发请求、不收集数据、本地加密、无第三方 SDK），版本号（自动读 `versionName` / `versionCode`），源代码（点击跳 GitHub repo）。Play 商店审核必备。

App 自己能切语言了：设置页有「语言」section，弹 dialog 选「跟随系统 / 中文（简体）/ English」，点完立即生效。Android 13+ 走系统 `LocaleManager`，低版本走 appcompat 1.7.0 兜底；用户上次的选择系统会持久化，重启 App 也不丢。

数据库正式上了 Migration：v0.3 schema 升到 v2（`plans` 表加 `archived` 字段，v0.4 归档功能用），配套 `WakePlanDatabaseMigrationTest` 走 `MigrationTestHelper` + `SupportOpenHelperFactory` 双验证（既验 SQL 兼容，也验加密路径走通）。`fallbackToDestructiveMigration` 已摘，正式版必须保住用户数据。

## 技术栈

- Kotlin 2.0 + Jetpack Compose（BOM 2024.10.00，含 Material 3）
- Hilt（KSP 编译期）+ Navigation Compose
- Room + SQLCipher 4.6.1（AES-256 加密本地数据库，16KB page size 兼容 Android 15+）
- DataStore Preferences + kotlinx.serialization
- java.time（API 26+ desugaring）
- WorkManager + AlarmManager（4 渠道本地通知）
- Gradle 8.7（wrapper 走阿里云镜像）

详见 `AGENTS.md` 关键路径说明。

## 开发

JDK 17+（Android Studio 自带 JBR 就行）。Android SDK Platform 35 + Build-Tools 35.0.0。模拟器 API 26+ 或 USB 调试的真机。

第一次跑：

```bash
echo "sdk.dir=D:\\Program Files\\Android\\Android SDK" > local.properties
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

第一次 sync 从阿里云镜像拉 Gradle 8.7 distribution + 依赖，约 2-5 分钟。

跑测试：

```bash
./gradlew :app:testDebugUnitTest
```

235 个 JVM 单测（JUnit 5 + mockk，不依赖 Android Keystore stub）。

## 目录

```
docs/
├── PRD.md                  # 完整产品需求文档
└── plan/00-roadmap.md      # 开发路线图

design/
├── colors_and_type.css     # Design Token 源
├── pages/*.html            # 各页 HTML 原型
└── partials/               # 公共片段

app/src/main/java/com/wakeplan/app/
├── ui/                     # Compose UI（today / plan / task / template / stats / settings / onboarding / widget）
├── domain/                 # 业务逻辑（model / repository / usecase）
├── data/                   # Room + DataStore + Repository + 备份 + 密钥 + 通知
└── di/                     # Hilt Modules
```

## 协议

Apache License 2.0，见 `LICENSE`。

## 文档版本

- 2026-08-11：v0.3 release（4 个 sub-phase 完成：隐私政策 + Per-app locale + Migration 测试 + 文档）
- 2026-08-11：v0.2 release（8 个 sub-phase 完成，235 个测试，SQLCipher 集成）
- 2026-08-07：仓库初始化 + 写路线图 + 骨架启动
