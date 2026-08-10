# Wake Plan

> 本地优先的多计划时间管理 Android App。一个用户同时跑多个人生计划——健身 / 学习 / 副业——用"时间段 + 任务 + 自动延期"模型，让一天乱了的时候不用一路手动改时间。

## 它解决什么

市面上日程类 App 要么只服务单一场景（待办 / 日历 / 习惯），要么把"计划"和"日程"混在一起。一旦某项任务超时，连锁反应全靠用户手动拖，过程损耗极大，到一天结束计划基本形同虚设。

Wake Plan 想做一件具体的事：**让你一天打开一次 App，看到现在该做什么；遇到卡顿一键延期，不用挨个改时间**。

## 核心差异点

- **时间分段 + 任务绑定** —— 一天先切段，再往段里塞任务
- **任务级"自动延期"开关** —— 超时任务可决定是否拖动后续任务，不强加全局策略
- **当前时刻聚合视图** —— 同一时段多计划的任务合并显示
- **时间线预览 + 内联冲突提示** —— 所见即所得，不弹干扰弹窗
- **零外联** —— App 不发任何网络请求，统计页也是本地算
- **本地加密** —— SQLCipher 4.6.1 AES-256，本机数据库不上云

## 路线图

| 阶段 | 状态 | 内容 |
|---|---|---|
| v0.1 MVP | ✅ 完成 | 计划 CRUD · 时间段 · 任务绑定 · 模板（基础）· 开始/完成 · 自动延期 · 当前时刻视图 · 冲突提示（内联）· 本地通知（任务开始+超时）· JSON 备份导出 |
| **v0.2** | ✅ 完成（2026-08-11） | 自动延期撤销 · 完成统计数字 + Canvas 图表 · 时间线日期切换 · 模板预览 + 一键新建 · i18n（中英双语）· 桌面 AppWidget · 快捷设置 Tile · **SQLCipher 4.6.1 加密本地 DB** |
| v0.3 | 候选 | Migration 测试 · 更多 v0.2 衍生项 |
| v1.0 | 候选 | 隐私政策 · 应用市场上架 · E2E 测试 |

完整计划在 [`docs/plan/00-roadmap.md`](docs/plan/00-roadmap.md)。

## v0.2 新增能力

- **自动延期撤销** —— Snackbar 5 秒窗口，撤销最近一次延期
- **完成统计** —— Stats tab：7/30 天完成数折线 + 按计划柱状 + 平均延期
- **时间线日期切换** —— ← / 今天 / → 翻日期，"今日"标签动态切换
- **模板预览 + 一键新建** —— 详情页显示 段 + 任务 树，"应用此模板"直接生成 plan
- **i18n** —— 中英双语（`values/strings.xml` + `values-en/strings.xml`，199 个 key 一一对应）
- **桌面 AppWidget** —— 4×2 mini timeline，PendingIntent 一键跳回 Today
- **快捷设置 Tile** —— 下拉面板里直接进 Today tab
- **SQLCipher 4.6.1 加密本地 DB** —— 32 字节随机 passphrase，AndroidKeyStore master key 包装后存 DataStore

## 技术栈

- Kotlin 2.0 + Jetpack Compose（BOM 2024.10.00，含 Material 3）
- Hilt（KSP 编译期）+ Navigation Compose
- Room + **SQLCipher 4.6.1**（AES-256 加密本地数据库，16KB page size 兼容 Android 15+）
- DataStore Preferences + kotlinx.serialization
- java.time（API 26+ desugaring）
- WorkManager + AlarmManager（4 渠道本地通知）
- Gradle 8.7（wrapper 走阿里云镜像）

详见 [AGENTS.md](AGENTS.md) 关键路径说明。

## 开发

### 前置环境

- JDK 17+（推荐用 Android Studio 自带 JBR）
- Android Studio Hedgehog (2023.1) 或更新
- Android SDK Platform 35 + Build-Tools 35.0.0
- 模拟器 API 26+ 或 USB 调试的真机

### 第一次跑起来

```bash
# 1. 写 local.properties（gitignored）
echo "sdk.dir=D:\\Program Files\\Android\\Android SDK" > local.properties

# 2. 同步 + 编译
./gradlew :app:assembleDebug

# 3. 装到设备
./gradlew :app:installDebug
```

第一次 sync 会从阿里云镜像拉 Gradle 8.7 distribution + 依赖，约 2-5 分钟。

### 跑测试

```bash
# 235 个 JVM 单测（JUnit 5 + mockk，不依赖 Android Keystore stub）
./gradlew :app:testDebugUnitTest
```

## 目录速览

```
docs/
├── PRD.md                  # 完整产品需求文档
└── plan/00-roadmap.md      # 开发路线图（Phase 拆解 + 决策表）

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

Apache License 2.0 — 见 [LICENSE](LICENSE)。

## 文档版本

- 2026-08-11：v0.2 release（8 个 sub-phase 完成，235 个测试，SQLCipher 集成）
- 2026-08-07：仓库初始化 + 写路线图 + 骨架启动
