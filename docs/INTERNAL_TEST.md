# Wake Plan v0.3.0 内测说明

> 2026-08-11 起的保守型内测。v0.3.0 是基于 v0.2 的稳定性 + 隐私 + 国际化补丁。
> 现阶段只对熟人小范围，不上应用市场。

## 安装

从 GitHub Release 页面下载 v0.3.0 release APK：

<https://github.com/uwakeme/wake-plan/releases/tag/v0.3.0>

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

跨版本迁移：
- 从 v0.2 装上来的 App：数据库 schema v1 → v2（加 archived 字段），自动 migration
- 从更早版本：未测过，建议卸了重装

## 测什么

按"日常用得最多 → 关键功能 → 边角"这个顺序：

### 必须测（主流程）
1. **新建计划 + 段 + 任务**：Plans tab → 右下 + → 填标题选色 → 进 plan 详情 → 加段（起止时间 + 重复规则） → 加任务（标题 + 时长）→ 保存
2. **今日时间线**：Today tab 翻日期（← / 今天 / →），确认段 + 任务在正确时间显示
3. **开始 + 完成 + 自动延期**：在 Today 屏上点任务「开始」，等段时间点「完成」或拖出时间轴，确认后续任务自动顺延
4. **撤销自动延期**：点完完成后 5 秒内 Snackbar 出现「撤销」按钮，点一下确认撤回
5. **冲突检测**：建两个时间段重叠的段，确认 plan 详情有红色冲突提示
6. **通知**：到 Settings 屏把 lead time 改成 5 分钟，建个 5 分钟内的任务，看通知能不能弹

### 应该测（v0.2 / v0.3 新功能）
7. **完成统计**：今日 tab 完成几个任务后切到 Stats tab，看 7 天 / 30 天数字 + 折线图 + 按计划柱状图是否对得上
8. **模板库**：Settings 屏点「模板库」→ 3 个内置模板 → 进详情看段 + 任务树 → 点「应用此模板」回 Plans
9. **桌面 AppWidget**：长按桌面 → 小部件 → 找 Wake Plan → 拖到桌面，看 mini timeline 是不是更新
10. **快捷设置 Tile**：下拉系统快捷栏 → 编辑 → 拖 Wake Plan tile 进去 → 点它是不是跳到 Today tab
11. **SQLCipher 加密**：装上后看 `/data/data/com.wakeplan.app/databases/wakeplan.db` 前 16 字节应该是随机的密文（不是 `SQLite format 3\0`）。备份导出后用文本编辑器看是 JSON，删 App 重装再用「导入备份」恢复
12. **设置 - 关于**：进 Settings 滚到底，「关于」section 看隐私政策 dialog 弹出来 + 跳 GitHub repo
13. **设置 - 语言切换**：进 Settings 切到「App 语言」→ 中文 / English / 跟随系统三选一，确认 UI 即时切

### 可选测（边角 + 容错）
14. **备份 - 导出明文 / 加密**：明文导出后用文本编辑器打开看是 JSON；加密导出要至少 8 位密码，导完再导入
15. **i18n 完整**：切成 English 后看所有屏（Today / Plans / Plan 详情 / 段编辑 / 任务编辑 / Template 详情 / Template 列表 / Settings / 备份 dialog）有没有漏翻
16. **横竖屏旋转**：竖屏建好计划 → 转横屏看 UI 有没有崩
17. **后台杀进程**：计划建好 → 长按后台杀掉 App → 重新打开看任务还在 + 通知有没有漏
18. **切日期（Yesterday / Tomorrow）**：Today tab 切到明天建个任务 → 切回来确认任务不在今日时间线

## 已知问题 / 不在 v0.3 范围

- **多语言只有中英** — 其他语言先按系统默认（=英文）
- **没有深色模式** — v0.4 才做
- **没有番茄钟 / 专注模式** — v0.4+ 候选
- **没有 CSV 导出** — v0.4 候选
- **没有快速录入** — 现在建计划要 3-4 步

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
