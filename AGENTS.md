# AGENTS.md

Wake Plan — 本地优先的多计划时间管理 Android App。

## Setup commands

- Build debug APK: `./gradlew :app:assembleDebug`
- Install to device: `./gradlew :app:installDebug`
- Run unit tests: `./gradlew :app:test`
- Run lint: `./gradlew :app:lintDebug`
- Clean: `./gradlew clean`

> 本地 `local.properties` (`sdk.dir`) 与 `gradle.properties` (`org.gradle.java.home`) 都指向本机路径，换机器需重写。

> 非交互 shell 里跑 `gradlew.bat` 必须先设 `JAVA_HOME`（本机 jbr 在 `D:\Program Files\Android\Android Studio\jbr`，
> 即 `local.properties` 的 `org.gradle.java.home`），否则直接报 `JAVA_HOME is not set and no 'java' command could be found`。
> aapt2 的本机变通同理放用户级 `~/.gradle/gradle.properties`，**不要**写进仓库。

## Project layout

- `app/` — Android app module（Kotlin / Compose / Hilt / Room / SQLCipher）
- `docs/` — PRD / 路线图 / 计划文档
- `docs/index.html` — 项目主页单文件（GitHub Pages 发布：源 `main` + `/docs`，站点 `www.uwakeme.top/wake-plan/`；改主页内容直接改这个文件再推 main）
- `designs/` — UI 设计稿（HTML 原型 + 界面截图）
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
- 所有测试通过再提交推送

## CI

- GitHub Actions 跑在境外 runner（Azure）上，**不能走阿里云镜像**：实测 `maven.aliyun.com`
  从 runner 返回 **502**，会让 Gradle 插件解析整体失败（报「Plugin [id: 'com.google.devtools.ksp']
  was not found」，即使官方源有该工件）。CI 命令一律带 `-PchinaMirror=false`——
  `settings.gradle.kts` 里镜像受该属性控制，本地开发默认仍走阿里云加速
- workflow 里的 `Probe plugin repositories` 步骤会把各仓库状态码打进日志，
  插件解析类失败先看这几行，别猜
- 2026-11-17 首次跑绿；此前 22 次运行全挂在插件解析这一步

## Commit conventions

- 主分支 `main`；初期单人开发阶段直接在 main 提交并推送，不建 feature 分支（等多人协作时再恢复分支 + PR 流程）
- 一个 commit 一个逻辑单元，不要大杂烩
- 平实中文 commit 消息，无 emoji
- 类型前缀：`feat:` / `fix:` / `docs:` / `refactor:` / `test:`
- 例子：`feat: 加 Plan entity 和 DAO 接口` / `fix: 修复段编辑器跨午夜 endTime 折算`

## Security

- 不要提交 `local.properties`（已在 `.gitignore`）
- 不要提交任何 secrets / 密钥 / token
- 仓库已于 2026-11-17 转为**公开** ⇒ 提交前确认没有密钥、token、内网地址被提交；git 历史里的文件同样对外可见
- 本地数据库走 SQLCipher 加密（master key 在 AndroidKeyStore）

## Release checklist

- 仓库已转**公开** ⇒ shields.io 能匿名取到 GitHub API，`github/v/release` 这类动态徽章已实测可用
  （2026-11-17 验证返回 `RELEASE: V0.6.0`）
- README 徽章目前仍是**静态**的（`/badge/version-x.y.z-color`），**每次发版要手动改**
  `README.md` 和 `README.en.md` 里的 `version-x.y.z`（各一处）；想换动态徽章就两处一起换
- **APK 从 tag 的干净 worktree 编**：`git worktree add <dir> <tag>` → 拷 `local.properties` →
  在 worktree 里 `assembleRelease` → `gh release create <tag> <apk>` → `git worktree remove --force`。
  别在主工作区编，否则未提交的 WIP 会被打进产物，APK 与 tag 对不上
- tag 之后若又改了 README（Release 页按 tag 渲染 README），要 `git tag -f -a <tag> -F <msg>`
  +`git push --force origin <tag>` 把 tag 移过去
- Release 正文用 `.release-notes.md`，发版前更新到当前版本
- 版本号改 `app/build.gradle.kts` 的 `versionCode` / `versionName`，并同步
  `docs/INTERNAL_TEST.md` 的安装段、迁移链与版本记录
