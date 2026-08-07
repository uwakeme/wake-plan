# AGENTS.md

> 这份文件给在仓库里工作的 agent 读（OpenCode / Cursor / Codex / Devin / Gemini CLI / Aider / 任何 Mavis session）。
> 不写给人类协作成员看，**人类文档在 `README.md`**，PRD/设计/计划在 `docs/`。

## 项目是什么

**Wake Plan** —— 本地优先的多计划时间管理 Android App。一个用户同时跑多个人生计划（健身 / 学习 / 副业），用"时间段 + 任务 + 自动延期"模型，让一天乱了的时候不用全部手动改时间。

完整背景：`docs/PRD.md` · 路线图：`docs/plan/00-roadmap.md` · UI 设计稿：`design/pages/*.html` + `design/colors_and_type.css`。

## 当前阶段

**Phase 0 · 骨架**（参考 `docs/plan/00-roadmap.md` 第 5 节）。
正在做的事：
- Gradle 工程（阿里云镜像 + 版本目录 + wrapper）
- Hilt + Room 空壳（5 entity 已声明，DAO 暂空）
- Compose 主题 + 3 tab 导航（Today / Plans / Settings，3 个 Screen 占位）
- AGENTS.md / README.md 到位
- 推 GitHub `wake-plan`（`uwakeme/wake-plan`）

**已完成 P0 US**：无（骨架阶段尚无可演示功能）
**下一阶段**：Phase 1 CRUD（计划/段/任务/模板基础 CRUD）

## 关键路径

| 路径 | 内容 | 备注 |
|---|---|---|
| `local.properties` | `sdk.dir=...` | **gitignored**，本机 Android SDK 路径 |
| `gradle.properties` | `org.gradle.java.home=...` | **gitignored 之外？— 当前是 commit 的**，含本机 JBR 路径 |
| `gradle/libs.versions.toml` | 所有依赖版本目录 | 改版本只动这里 |
| `settings.gradle.kts` | 阿里云镜像三件套 | 国内必走 |
| `gradle/wrapper/gradle-wrapper.properties` | `distributionUrl` 走 `mirrors.aliyun.com/macports/distfiles/gradle/` | 国内 wrapper 必走 |
| `app/build.gradle.kts` | app module 构建脚本 | |
| `app/src/main/java/com/wakeplan/app/` | Kotlin 源码（ui / domain / data / di） | |
| `app/src/main/res/values/themes.xml` | 仅 splash 主题；Compose Material3 主题在 `ui/theme/Theme.kt` | |
| `app/schemas/` | Room schema 导出（JSON） | commit 上去便于 Migration 测试 |

## 构建命令

所有命令在仓库根目录执行。

| 命令 | 用途 |
|---|---|
| `./gradlew :app:assembleDebug` | 出 debug APK |
| `./gradlew :app:installDebug` | 装到当前连接的设备/模拟器 |
| `./gradlew :app:lintDebug` | Android Lint |
| `./gradlew :app:test` | JVM 单元测试 |
| `./gradlew :app:testDebugUnitTest` | 显式跑 debug 变体的单元测试 |
| `./gradlew :app:connectedAndroidTest` | 仪器测试（需要设备） |
| `./gradlew clean` | 清 build 产物 |
| `./gradlew :app:dependencies` | 看依赖树（排查冲突） |

> **重要**：环境没改全局 PATH，JDK 走 `gradle.properties` 的 `org.gradle.java.home`；Android SDK 走 `local.properties` 的 `sdk.dir`。换机器要重新写这两个文件。

## 代码组织

```
app/src/main/java/com/wakeplan/app/
├── App.kt                    # @HiltAndroidApp
├── MainActivity.kt           # @AndroidEntryPoint + setContent { WakePlanTheme { AppNavHost() } }
├── ui/
│   ├── theme/                # Color/Type/Shape/Theme
│   ├── nav/                  # AppNavHost + BottomTabBar
│   ├── today/                # 当前时刻屏
│   ├── plan/                 # 计划列表 / 详情 / 新建
│   ├── task/                 # 任务编辑
│   ├── template/             # 模板库
│   ├── settings/             # 设置
│   └── common/               # 通用 Composable
├── domain/
│   ├── model/                # 纯 Kotlin data class（无 Android 依赖）
│   ├── repository/           # Repository 接口
│   └── usecase/              # 业务用例（AutoExtendCalculator / ConflictDetector）
├── data/
│   ├── db/                   # Room: entity / dao / converter / WakePlanDatabase
│   ├── prefs/                # DataStore
│   ├── repository/           # Repository 实现
│   └── backup/               # JSON 备份导出/导入 + 加密
└── di/                       # Hilt Modules
```

**规则**：
- `domain/` 不依赖 `data/` 和 `ui/`
- `data/` 不依赖 `ui/`
- `ui/` 通过 Hilt 拿 Repository；ViewModel 不直接 new
- `data/db/entity/` 用 Room 注解；`domain/model/` 用纯 Kotlin data class（不带 Room 注解），由 Repository 转换

## 命名约定

- 包名：`com.wakeplan.app`（已定）
- 文件名：`<Thing>.kt`（如 `PlanRepository.kt`，不写 `PlanRepositoryImpl.kt`，实现放 `data/repository/`）
- Composable：`XxxScreen` 屏 / `XxxCard` 卡片 / `XxxButton` 按钮；私有 Composable 加 `private` 修饰
- ViewModel：`XxxViewModel`，文件名同
- DAO：`XxxDao`
- Entity：`<Thing>Entity`（Room 用），`<Thing>`（domain 用）—— 同名不同包，用 import 区分
- 测试：`<Thing>Test.kt`（JUnit5）

## 提交约定

- **粒度**：一个 commit 一个逻辑单元（一个 entity / 一个屏 / 一次重构），不要大杂烩
- **消息风格**（用户偏好）：平实中文，无 emoji 装饰
  - ✅ `feat: 加 Plan entity 和 DAO 接口`
  - ✅ `fix: 修复段编辑器跨午夜 endTime 折算`
  - ✅ `docs: 写 AGENTS.md`
  - ❌ `🎉 feat: ✨ 全新 Plan entity 🚀`
  - ❌ `WIP`

## 注意事项

1. **KSP 而不是 KAPT** —— Hilt 和 Room 都用 KSP，编译快很多
2. **Room schema export** —— `app/build.gradle.kts` 配 `room.schemaLocation` 把 schema JSON 导出到 `app/schemas/`，commit 上去便于写 Migration
3. **SQLCipher** —— 用 `net.zetetic:android-database-sqlcipher` + `androidx.sqlite:sqlite-ktx`；数据库打开走 `SupportFactory(passphrase)`，`passphrase` 存 Android Keystore
4. **Compose Material3 主题** —— `WakePlanTheme` 是 `MaterialTheme` 的薄包装，token 来自 `design/colors_and_type.css`；不要另起 `MaterialTheme` 子品牌
5. **不要用 `&&` 在 PowerShell 里** —— 改用 `;` / `if ($?) { ... }`
6. **网络约束** —— 本机 HTTPS 443 不通，SSH 22 通；GitHub 走 SSH URL（`git@github.com:uwakeme/wake-plan.git`）

## 跑这个项目的人要知道的事

- 我（agent）每完成一个 Phase，会更新 `docs/plan/00-roadmap.md` 的勾选状态
- 任何对 PRD / 路线图的修改，先在 plan 文档里改、再改代码
- 用户在 plan-mode 拍板的 5.3 决策（`docs/plan/00-roadmap.md` 第 1 节）有冲突时，以 plan 文档为准
- SQLCipher 包体 +5MB，但用户隐私数据值得；不要为了瘦身去掉
