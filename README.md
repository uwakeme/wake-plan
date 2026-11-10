<a id="readme-top"></a>

<div align="center">
  <img src="./designs/banner/wake-plan-banner.png" alt="Wake Plan" width="100%">
</div>

# Wake Plan

<p align="center">
  本地优先的多计划时间管理 Android App<br>
  时间段 + 任务 + 自动延期，一天打开一次就知道现在该做什么
</p>

<div align="center">

[![Version][version-shield]][version-url]
[![License][license-shield]][license-url]
[![CI][ci-shield]][ci-url]
[![Contributors][contributors-shield]][contributors-url]
[![Kotlin][kotlin-shield]][kotlin-url]
[![Android][android-shield]][android-url]

</div>

<details>
<summary>目录</summary>

- [它解决什么](#它解决什么)
- [核心模型](#核心模型)
- [功能一览](#功能一览)
- [隐私与安全](#隐私与安全)
- [已知限制](#已知限制)
- [获取与安装](#获取与安装)
- [从源码构建](#从源码构建)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [路线图](#路线图)
- [版本记录](#版本记录)
- [参与开发](#参与开发)
- [协议](#协议)

</details>

## 它解决什么

一个人同时跑好几条线：健身、考证、副业写作。市面上的日程 App 大多只服务一种场景，要么是纯待办，要么是日历，要么是习惯打卡。真要把「计划」和「日程」放到一起用，得在几个 App 之间来回切。

更麻烦的是超时。今天某个任务一超时间，后面的安排全靠手动一个个往后拖，拖到中午基本就放弃了，计划也就作废。

Wake Plan 把时间先切段，段里放任务。任务超时了，后面没锁定的任务自动往后延，你只需要点一次「延期」。一天打开一次 App，看到的就是当前时刻该干的那件事。

三条硬约束贯穿整个项目：App 不发任何网络请求；数据只存在本机并且是加密的；没有账号、没有后端、没有第三方 SDK。

## 核心模型

| 概念 | 是什么 | 关键行为 |
| --- | --- | --- |
| 计划 Plan | 一条线，比如「考研英语」 | 独立计时，跟别的计划互不干扰 |
| 任务 Task | 计划里的一件具体事 | 自带起止时间、重复规则、锁定标记 |
| 任务实例 TaskCompletion | 任务在某一天的落地记录 | PENDING → IN_PROGRESS → COMPLETED / SKIPPED |

自动延期的规则就四条：

- 实际结束时间晚于计划结束时间，超出的分钟数向下取整
- 同一计划里还没开始、没锁定的后续任务整体往后推，推完继续看下一个
- 遇到锁定的任务就停下，它和它后面的任务留在原位
- 延期只在同一个计划内发生，别的计划不受影响

```text
10:00  背单词     实际 10:40 结束  →  溢出 40 分钟
11:00  做阅读     顺延到 11:40
12:00  复盘       顺延到 12:40
13:00  午休      [锁定]    遇到它停止级联
```

## 功能一览

### 现在

- 打开就是当前时刻的聚合视图，多个计划的任务按时间轴叠在一起
- 24 小时水平时间线，红线是当前时刻，可以往前翻一天、往后翻一天
- 一键「开始 / 完成」，完成后弹可撤销的 Snackbar，最多排队 5 条
- 底部弹层快速建任务，默认挂到最近用的那个计划上

### 计划

- 计划列表带 10 种预设色，也能自己填 hex，起始日期可选
- 任务自带起止时间，重复规则支持仅一次、每天、工作日、自定义星期
- 跨午夜的任务（比如 23:00 到次日 00:30）按次日折算，不用手动改日期
- 冲突以内联色条标在列表里，不弹窗
- 计划可以归档，归档后不再进时间线、延期和统计
- 内置 3 个模板（标准晨练、标准学习、周末阅读），套用后直接生成带任务的计划

### 专注

- 从任务卡片进全屏计时环，专注、短休、长休三段轮次
- 前台 Service 跑倒计时，锁屏也留一条常驻通知
- 一轮结束直接打卡，专注时长落库并计入统计
- 同一时刻只允许一个任务处于进行中

### 统计

- 近 7 天、近 30 天、本月三个窗口切换
- 每日完成数折线、按计划柱状、完成率、平均延期
- 专注时长按天、按计划一起算进去
- 明细可以导出 CSV，带 UTF-8 BOM，Excel 打开不乱码

### 设置

- 通知渠道分开配：任务开始（提前 0/5/10/15 分钟）、任务超时、任务完成、每日回顾、专注计时
- 每日回顾默认 21:00 推一条当天的完成率
- 语言跟随系统、中文、English 三选一，切换后立即生效
- 备份导出导入 JSON，可以选 passphrase 加密（AES-256）
- 数据保留策略：永久、90 天、1 年，只清终态记录，进行中的永远不动
- 隐私政策、版本号、源码链接都写在「关于」里

### 通知与桌面

- 桌面 AppWidget 是 4×2 的迷你时间线，打卡后自动刷新
- 下拉面板的快捷设置 Tile 走同一个入口
- 开机、改系统时间、改时区之后闹钟会重新排一遍

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 隐私与安全

- 没有网络请求。整个 App 不含任何网络库，统计口径全在本机算
- 本地库用 SQLCipher 4.6.1 加密（AES-256），密钥在 AndroidKeyStore 里生成并包装
- 无账号、无后端、无第三方 SDK、没有埋点
- 数据随时能导出成 JSON 或 CSV，也能导入回来换机
- 通知权限只在引导页申请一次，被拒之后在设置页留了系统设置入口

## 已知限制

- 专注计时依赖前台 Service，部分国产 ROM 的后台限制可能让倒计时通知中断
- 进程被杀之后，正在进行的专注场次不会恢复，重新打开会看到未结算的状态
- 精确闹钟在部分 ROM 上需要额外授权，已在设置页给出提示
- v0.6 之前导出的备份里没有 `focus_sessions` 字段，导入时按空集合处理

## 获取与安装

- 从 [Releases][releases-url] 下载 `app-release.apk`
- 系统要求 Android 8.0（API 26）及以上
- 第一次装会提示「未知来源」，按系统引导允许即可
- 目前 APK 用 debug keystore 临时签名，正式上架前会换成正式 keystore。提示签名冲突的话，先卸载再装

内测的测试清单和已知问题写在 [`docs/INTERNAL_TEST.md`](docs/INTERNAL_TEST.md)。

## 从源码构建

### 环境要求

- JDK 17（Android Studio 自带的 JBR 就行）
- Android SDK Platform 35 + Build-Tools 35.0.0
- 模拟器 API 26 以上，或者开了 USB 调试的真机

### 第一次跑

```bash
git clone git@github.com:uwakeme/wake-plan.git
cd wake-plan

# local.properties 不在仓库里，第一次要自己写一份
echo "sdk.dir=D:\\Program Files\\Android\\Android SDK" > local.properties

./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

> [!NOTE]
> 依赖和 Gradle wrapper 都走国内镜像。`settings.gradle.kts` 配了阿里云 maven 三件套，wrapper 指向腾讯云的 gradle 镜像，第一次 sync 大概 2 到 5 分钟。

### 跑测试

```bash
./gradlew :app:testDebugUnitTest      # JVM 单测
./gradlew :app:lintDebug              # lint
./gradlew :app:connectedAndroidTest   # 仪器测试，需要连着设备
```

JVM 单测目前 326 个用例、56 个测试套件，JUnit 5 + MockK + Turbine，不依赖 Robolectric。数据库迁移测试放在 `androidTest`，用 Room 的 `MigrationTestHelper` 跑，会连 SQLCipher 加密路径一起验。

### 提交前

`AGENTS.md` 里写了门槛：单测全绿、lint 0 error 才能推。GitHub Actions 上跑的是同样的两条。

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose（BOM 2024.10.00，Material 3） |
| 状态 | StateFlow + `collectAsStateWithLifecycle` |
| 依赖注入 | Hilt 2.52（走 KSP，不用 KAPT） |
| 导航 | Navigation Compose 2.8.3 |
| 数据库 | Room 2.6.1 + SQLCipher 4.6.1（AES-256） |
| 偏好 | DataStore Preferences 1.1.1 |
| 序列化 | kotlinx.serialization 1.7.3 |
| 定时 | WorkManager 2.9.1 + AlarmManager |
| 时间 | java.time + desugaring（API 26+） |
| 测试 | JUnit 5 + MockK + Turbine + Room Testing |
| 构建 | Gradle 8.7 / AGP 8.5.2 |

Hilt 和 Room 都用 KSP，编译期生成代码。SQLCipher 用的 4.6.1，16KB page size，对 Android 15+ 没问题。

## 项目结构

```text
app/src/main/java/com/wakeplan/app/
├── ui/          Compose 界面：today / plan / task / template / focus / stats / settings / onboarding / widget
├── domain/      业务逻辑：model（纯 Kotlin）/ repository 接口 / usecase
├── data/        Room entity 与 DAO、DataStore、Repository 实现、备份、密钥、通知
└── di/          Hilt Module

docs/
├── PRD.md                 产品需求文档
├── INTERNAL_TEST.md       内测测试清单
├── CODE_REVIEW.md         代码审查报告
└── plan/00-roadmap.md     路线图与各版本执行结果

designs/
├── design-prototype.html  界面原型
└── banner/                README 顶图
```

数据流是单向的：Compose → ViewModel（StateFlow）→ UseCase → Repository → Room / DataStore。`domain/` 不依赖 `data/` 和 `ui/`，`data/` 不依赖 `ui/`。

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## 路线图

| 版本 | 状态 | 内容 |
| --- | --- | --- |
| v0.1 | 完成 | 计划 CRUD、任务时间、模板、开始/完成、自动延期、当前时刻视图、冲突内联提示、通知、JSON 备份 |
| v0.2 | 完成 | 延期撤销、完成统计面板与 Canvas 图表、时间线翻日期、模板预览与一键新建、中英双语、AppWidget、快捷设置 Tile、SQLCipher 加密 |
| v0.3 | 完成 | 隐私政策与「关于」、Per-app locale 切换、Room Migration 测试基础设施 |
| v0.4 / v0.4.1 | 完成 | 备份导入事务化、重启重排闹钟、模板补全、计划归档、快速录入、冲突可视化；v0.4.1 是一轮正确性修复，含链式延期落库、ONCE 语义、通知调度、统计口径 |
| v0.5 | 完成 | 每日回顾通知、按月统计、CSV 导出、撤销队列、数据保留策略 |
| v0.6 | 完成 | 番茄钟专注页：`focus_sessions` 表（schema v5）、前台 Service 倒计时、专注时长进统计 |
| v1.0 | 候选 | 应用市场上架、E2E 测试、正式 keystore 签名 |

## 版本记录

- 2026-10-09：v0.6 收口，versionCode 9
- 2026-10-09：v0.5 收口，versionCode 8
- 2026-10-09：v0.4.1 正确性修复版，versionCode 7
- 2026-09-11：v0.4 内测收口，versionCode 6
- 2026-08-11：v0.3 release
- 2026-08-11：v0.2 release
- 2026-08-07：仓库初始化

## 参与开发

目前单人维护，改动直接提 PR 到 `main`。

- 遇到问题开 issue，崩溃日志请附上 `adb logcat -d -t 500 AndroidRuntime:E *:S` 的输出
- commit 用中文，前缀 `feat:` / `fix:` / `docs:` / `refactor:` / `test:`，一个 commit 一个逻辑单元
- 改行为要同步加测试，推之前 `./gradlew :app:testDebugUnitTest` 全绿、`:app:lintDebug` 0 error
- 动数据库表结构单独一个 commit，schema JSON 跟着提交

## 协议

Apache License 2.0，见 [LICENSE](LICENSE)。

<!-- MARKDOWN LINKS & IMAGES -->

[version-shield]: https://img.shields.io/github/v/release/uwakeme/wake-plan.svg?style=for-the-badge
[version-url]: https://github.com/uwakeme/wake-plan/releases
[license-shield]: https://img.shields.io/github/license/uwakeme/wake-plan.svg?style=for-the-badge
[license-url]: https://github.com/uwakeme/wake-plan/blob/main/LICENSE
[ci-shield]: https://img.shields.io/github/actions/workflow/status/uwakeme/wake-plan/ci.yml?branch=main&style=for-the-badge
[ci-url]: https://github.com/uwakeme/wake-plan/actions/workflows/ci.yml
[contributors-shield]: https://img.shields.io/github/contributors/uwakeme/wake-plan.svg?style=for-the-badge
[contributors-url]: https://github.com/uwakeme/wake-plan/graphs/contributors
[kotlin-shield]: https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white
[kotlin-url]: https://kotlinlang.org
[android-shield]: https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white
[android-url]: https://developer.android.com
[releases-url]: https://github.com/uwakeme/wake-plan/releases
