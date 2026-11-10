<a id="readme-top"></a>

<div align="center">
  <img src="./designs/banner/wake-plan-banner.png" alt="Wake Plan" width="100%">
</div>

# Wake Plan

<p align="center">
  A local-first Android app for juggling several plans at once<br>
  Time blocks, tasks, and automatic rollover, so one glance tells you what to do right now
</p>

<div align="center">

[![Version][version-shield]][version-url]
[![License][license-shield]][license-url]
[![Kotlin][kotlin-shield]][kotlin-url]
[![Android][android-shield]][android-url]

</div>

<details>
<summary>Table of Contents</summary>

- [What It Solves](#what-it-solves)
- [Core Model](#core-model)
- [Features](#features)
- [Privacy and Security](#privacy-and-security)
- [Known Limitations](#known-limitations)
- [Getting the App](#getting-the-app)
- [Building from Source](#building-from-source)
- [Tech Stack](#tech-stack)
- [Project Layout](#project-layout)
- [Roadmap](#roadmap)
- [Release History](#release-history)
- [Contributing](#contributing)
- [License](#license)

</details>

## What It Solves

One person, several tracks at once: the gym, a certification exam, a side business. Most scheduling apps serve exactly one job, whether that's a to-do list, a calendar, or a habit tracker. If you want plans and appointments to live together, you end up switching between apps all day.

Timeouts are the worse half. When one task runs long, everything after it has to be dragged forward by hand, one by one. By noon the day is a mess and the plan is dead.

Wake Plan splits the day into blocks first and drops tasks into them. When a task overruns, the unlocked tasks behind it roll forward on their own, so the only thing you touch is a single "delay" action. Open the app once a day and it shows the one thing that matters at this moment.

Three constraints shape the whole project: the app makes no network requests, all data stays on the device and is encrypted, and there is no account, no backend, and no third-party SDK.

## Core Model

| Concept | What it is | How it behaves |
| --- | --- | --- |
| Plan | One track, say "exam English" | Times itself independently, never interferes with other plans |
| Task | One concrete thing inside a plan | Carries its own start/end time, repeat rule, and lock flag |
| TaskCompletion | The record of a task on a given day | PENDING → IN_PROGRESS → COMPLETED / SKIPPED |

Automatic rollover comes down to four rules:

- Any minutes between the planned end and the actual end are counted, rounded down
- Later tasks in the same plan that have not started and are not locked shift forward, and the next one gets checked too
- A locked task stops the cascade, and it plus everything behind it stay where they are
- Rollover never crosses plan boundaries

```text
10:00  Vocabulary    finished at 10:40  →  40 minutes over
11:00  Reading      pushed to 11:40
12:00  Review       pushed to 12:40
13:00  Lunch      [locked]  cascade stops here
```

## Features

### Now

- Opens straight onto an aggregate view of the current moment, with tasks from every plan stacked on one timeline
- A 24-hour horizontal timeline where the red line marks the current time, and you can step a day back or forward
- One-tap start and finish, with an undoable Snackbar afterwards, up to five queued
- A bottom sheet for quick entry that files the task under your most recent plan by default

### Plans

- Ten preset colors per plan, plus a free-form hex value and a pickable start date
- Tasks carry their own start and end time, repeating once, daily, on weekdays, or on days you choose
- Tasks that cross midnight, like 23:00 to 00:30, roll into the next day without any date fiddling
- Conflicts show up as an inline color bar in the list rather than a modal
- Plans can be archived, and archived ones drop out of the timeline, rollover, and stats
- Three built-in templates (standard morning workout, standard study, weekend reading) that generate a plan with tasks already in it

### Focus

- A full-screen timer ring opens from a task card, cycling through focus, short break, and long break
- A foreground service runs the countdown and keeps a persistent notification on screen
- Finishing a round checks the task in, and the time lands in your stats
- Only one task may be in progress at a time

### Stats

- Switch between the last 7 days, the last 30 days, and the current month
- A line for daily completions, bars per plan, a completion rate, and average overrun
- Focus time is counted in alongside the rest, by day and by plan
- Detail rows export to CSV with a UTF-8 BOM, so Excel opens them without mangling the text

### Settings

- Each notification channel is configured separately: task starting (0, 5, 10, or 15 minutes ahead), task overdue, task completed, daily recap, and focus timer
- The daily recap lands at 21:00 with that day's completion rate
- Language follows the system, or you pick Chinese or English, and the change applies right away
- Backups export and import as JSON, optionally encrypted with a passphrase (AES-256)
- Data retention is either forever, 90 days, or one year, and it only ever removes finished records
- Privacy policy, version number, and source link all live in the About section

### Notifications and Home Screen

- The home screen widget shows a 4×2 mini timeline that refreshes itself after you check in
- The quick settings tile opens the same view
- Alarms get rescheduled after a reboot, a system clock change, or a timezone change

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Privacy and Security

- No network requests. The app ships no networking library at all, and every statistic is computed locally
- The local database is encrypted with SQLCipher 4.6.1 (AES-256), and the key is generated and wrapped inside AndroidKeyStore
- No account, no backend, no third-party SDK, no analytics
- Your data can be exported to JSON or CSV at any time, and imported back onto another device
- Notification permission is requested once during onboarding, and the settings screen keeps a shortcut to the system panel if you decline

## Known Limitations

- The focus timer runs as a foreground service, and aggressive background limits on some Chinese OEM builds can cut the countdown notification short
- If the process is killed, an in-flight focus session does not come back; you reopen to an unsettled state
- Exact alarms need extra permission on some ROMs, and the settings screen says so
- Backups exported before v0.6 have no `focus_sessions` field, and the importer treats it as an empty list

## Getting the App

- Download `app-release.apk` from [Releases][releases-url]
- Android 8.0 (API 26) or newer
- The first install asks you to allow installs from unknown sources, which is the normal system prompt
- The APK is currently signed with a debug keystore and will move to a real one before the store release. If you see a signature mismatch, uninstall the old copy first

The internal test checklist and known issues live in [`docs/INTERNAL_TEST.md`](docs/INTERNAL_TEST.md).

## Building from Source

### Requirements

- JDK 17 (the JBR bundled with Android Studio is fine)
- Android SDK Platform 35 and Build-Tools 35.0.0
- An emulator on API 26+, or a device with USB debugging enabled

### First Run

```bash
git clone git@github.com:uwakeme/wake-plan.git
cd wake-plan

# local.properties is not in the repo, so write your own
echo "sdk.dir=D:\\Program Files\\Android\\Android SDK" > local.properties

./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

> [!NOTE]
> Both dependencies and the Gradle wrapper come from Chinese mirrors. `settings.gradle.kts` sets up the Aliyun maven trio and the wrapper points at Tencent's Gradle mirror, so the first sync takes roughly two to five minutes.

### Running the Tests

```bash
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:lintDebug              # lint
./gradlew :app:connectedAndroidTest   # instrumented tests, needs a device
```

The JVM suite currently holds 326 cases across 56 test classes, built on JUnit 5, MockK, and Turbine with no Robolectric dependency. Database migration tests live in `androidTest` and run through Room's `MigrationTestHelper`, which exercises the SQLCipher path as well.

### Before You Push

`AGENTS.md` sets the bar: unit tests green and lint at zero errors. GitHub Actions runs exactly those two.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Tech Stack

| Layer | Choice |
| --- | --- |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.10.00, Material 3) |
| State | StateFlow + `collectAsStateWithLifecycle` |
| Dependency injection | Hilt 2.52 (KSP, not KAPT) |
| Navigation | Navigation Compose 2.8.3 |
| Database | Room 2.6.1 + SQLCipher 4.6.1 (AES-256) |
| Preferences | DataStore Preferences 1.1.1 |
| Serialization | kotlinx.serialization 1.7.3 |
| Scheduling | WorkManager 2.9.1 + AlarmManager |
| Time | java.time with desugaring (API 26+) |
| Testing | JUnit 5 + MockK + Turbine + Room Testing |
| Build | Gradle 8.7 / AGP 8.5.2 |

Hilt and Room both run on KSP, so the code is generated at compile time. SQLCipher is on 4.6.1 with a 16KB page size, which is what Android 15+ expects.

## Project Layout

```text
app/src/main/java/com/wakeplan/app/
├── ui/          Compose screens: today / plan / task / template / focus / stats / settings / onboarding / widget
├── domain/      Business logic: model (plain Kotlin) / repository interfaces / usecases
├── data/        Room entities and DAOs, DataStore, repository implementations, backup, keys, notifications
└── di/          Hilt modules

docs/
├── PRD.md                 Product requirements
├── INTERNAL_TEST.md       Internal test checklist
├── CODE_REVIEW.md         Code review report
└── plan/00-roadmap.md     Roadmap and per-version results

designs/
├── design-prototype.html  UI prototype
└── banner/                README banner
```

Data flows one way: Compose → ViewModel (StateFlow) → UseCase → Repository → Room / DataStore. `domain/` depends on neither `data/` nor `ui/`, and `data/` does not depend on `ui/`.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

## Roadmap

| Version | Status | Contents |
| --- | --- | --- |
| v0.1 | Done | Plan CRUD, task timing, templates, start/finish, automatic rollover, current-moment view, inline conflict hints, notifications, JSON backup |
| v0.2 | Done | Undo for rollover, stats panel with Canvas charts, timeline date navigation, template preview and one-tap plan creation, Chinese/English, AppWidget, quick settings tile, SQLCipher encryption |
| v0.3 | Done | Privacy policy and About section, per-app locale switching, Room migration test infrastructure |
| v0.4 / v0.4.1 | Done | Transactional backup import, alarm rescheduling on reboot, completed templates, plan archiving, quick entry, conflict visualization; v0.4.1 was a correctness pass covering persisted rollover, ONCE semantics, notification scheduling, and stats definitions |
| v0.5 | Done | Daily recap notification, monthly stats, CSV export, undo queue, data retention policy |
| v0.6 | Done | Pomodoro focus screen: `focus_sessions` table (schema v5), foreground service countdown, focus time in stats |
| v1.0 | Planned | Store release, E2E tests, a real release keystore |

## Release History

- 2026-10-09: v0.6 wrapped, versionCode 9
- 2026-10-09: v0.5 wrapped, versionCode 8
- 2026-10-09: v0.4.1 correctness release, versionCode 7
- 2026-09-11: v0.4 internal test wrap, versionCode 6
- 2026-08-11: v0.3 release
- 2026-08-11: v0.2 release
- 2026-08-07: repository initialized

## Contributing

This is a one-person project for now, so pull requests go straight at `main`.

- Open an issue when something breaks, and attach the output of `adb logcat -d -t 500 AndroidRuntime:E *:S` if it crashed
- Commit messages are written in Chinese with a `feat:` / `fix:` / `docs:` / `refactor:` / `test:` prefix, one logical unit each
- Behavior changes come with tests; before pushing, run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug`
- Schema changes get their own commit, with the exported schema JSON alongside

## License

Apache License 2.0, see [LICENSE](LICENSE).

<!-- MARKDOWN LINKS & IMAGES -->

[version-shield]: https://img.shields.io/badge/version-0.6.0-blue?style=for-the-badge
[version-url]: https://github.com/uwakeme/wake-plan/releases
[license-shield]: https://img.shields.io/badge/license-Apache--2.0-green?style=for-the-badge
[license-url]: https://github.com/uwakeme/wake-plan/blob/main/LICENSE
[kotlin-shield]: https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white
[kotlin-url]: https://kotlinlang.org
[android-shield]: https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white
[android-url]: https://developer.android.com
[releases-url]: https://github.com/uwakeme/wake-plan/releases
