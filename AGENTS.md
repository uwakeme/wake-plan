# AGENTS.md

Wake Plan — 本地优先的多计划时间管理 Android App。

## Setup commands

- Build debug APK: `./gradlew :app:assembleDebug`
- Install to device: `./gradlew :app:installDebug`
- Run unit tests: `./gradlew :app:test`
- Run lint: `./gradlew :app:lintDebug`
- Clean: `./gradlew clean`

> 本地 `local.properties` (`sdk.dir`) 与 `gradle.properties` (`org.gradle.java.home`) 都指向本机路径，换机器需重写。

## Project layout

- `app/` — Android app module（Kotlin / Compose / Hilt / Room / SQLCipher）
- `docs/` — PRD / 路线图 / 计划文档
- `design/` — UI 设计稿（HTML + CSS）
- `app/schemas/` — Room schema 导出（commit 上去便于 Migration 测试）
- `gradle/libs.versions.toml` — 依赖版本目录

## Code style

- Kotlin 官方风格；Compose Material3 主题在 `ui/theme/Theme.kt`
- Hilt + Room 都用 KSP（不用 KAPT）
- `domain/` 不依赖 `data/` 和 `ui/`；`data/` 不依赖 `ui/`
- `ui/` 通过 Hilt 拿 Repository，ViewModel 不直接 new
- Room entity 走 `data/db/entity/`，domain model 走纯 Kotlin data class，由 Repository 转换

## Testing instructions

- JVM 单元测试：`./gradlew :app:testDebugUnitTest`（JUnit 5 + MockK）
- 仪器测试：`./gradlew :app:connectedAndroidTest`（需设备）
- 新增 behavior 必须同步加测试（参考同包已有 `*Test.kt`）
- 所有测试通过再开 PR

## PR & commit conventions

- 主分支 `main`；从 main checkout feature 分支，不要直接 push
- 一个 commit 一个逻辑单元，不要大杂烩
- 平实中文 commit 消息，无 emoji
- 类型前缀：`feat:` / `fix:` / `docs:` / `refactor:` / `test:`
- 例子：`feat: 加 Plan entity 和 DAO 接口` / `fix: 修复段编辑器跨午夜 endTime 折算`

## Security

- 不要提交 `local.properties`（已在 `.gitignore`）
- 不要提交任何 secrets / 密钥 / token
- 本地数据库走 SQLCipher 加密（master key 在 AndroidKeyStore）
