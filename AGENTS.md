# AGENTS.md

Rexi is an Android app for keeping track of medicines of any kind: tablets, capsules, liquids, drops, sprays, inhalers, injections, creams and patches. It reminds you when a dose is due, keeps reminding until you answer, warns before you run out, and keeps a simple record. Everything stays on the phone. The [README](README.md) explains what it does for the people who use it.

## Setup

- JDK 21 and the Android SDK with API level 37.
- One module, `:app`: Kotlin 2.4, the Android Gradle plugin 9 with its built-in Kotlin, Jetpack Compose, Room through KSP, Glance for the home screen widgets, and Biometric for the app lock.
- Versions live in `gradle/libs.versions.toml`. Add dependencies there, not in the build files.
- Git hooks run through [Lefthook](https://lefthook.dev). Set them up once per clone with `brew install lefthook && lefthook install`.

## Commands

| Command | What it does |
| --- | --- |
| `./gradlew check` | Formatting, Android Lint and every unit test. Work isn't done until this passes. |
| `./gradlew spotlessApply` | Fixes formatting. |
| `./gradlew :app:testDebugUnitTest --tests 'top.yeetfactory.rexi.core.DayBookTest'` | Runs one test class. |
| `./gradlew :app:lintDebug` | Android Lint only. The report is in `app/build/reports/`. |
| `./gradlew :app:assembleDebug` | Builds a debug APK. |
| `adb shell am broadcast -n top.yeetfactory.rexi/.debug.SampleData` | Debug builds only: replaces everything with sample medicines and two weeks of history. |

Tests run on the JVM, with Robolectric for Android code. They don't need a phone or an emulator.

## Git hooks

The hooks are in `lefthook.yml`.

- **Before a commit:** no trailing spaces or conflict markers, no signing files, and `spotlessCheck` when Kotlin files are staged. The hook never fixes formatting itself. Run `./gradlew spotlessApply`, review what it changed, and stage it.
- **On the commit message:** the first line must be a Conventional Commit.
- **Before a push:** `./gradlew check`, then a check that the build left no uncommitted change in `app/schemas/`.

Don't skip a hook with `--no-verify`. Fix what it found.

## Layout

The code is in `app/src/main/java/top/yeetfactory/rexi/`.

- `core/` holds the rules, in plain Kotlin with no Android in them: the model enums (`Model.kt`), which days a medicine is due (`Schedule`), how a day's doses fall into the four compartments and when they become due or missed (`DayBook`), supply and run-out dates (`Supply`), the as-needed guard (`AsNeeded`) and every piece of text made from numbers and dates (`Format`). New logic goes here, with a test.
- `data/` holds the Room database and its queries (`Database.kt`), the entities (`Model.kt`), `Repository`, which keeps the supply count in step with every dose, `SettingsStore`, and `Backup`.
- `remind/` holds the reminder engine. `Reminders` works out every alarm again from the database each time. `DoseActions` is the one way to take, skip or snooze, used by the app, notifications, the full screen reminder and the widget. `Notifier` builds the notifications and `RexiReceiver` handles alarms, notification buttons, boot and time zone changes. `ReminderActivity` is the full screen reminder.
- `widget/` holds the two Glance widgets. `report/` makes the PDF report for a doctor.
- `ui/` holds the Compose screens. `Theme.kt` is the palette (light and dark, with a colour set for each part of the day) and the Figtree type scale. `Kit.kt` holds the shared parts, `RexiIcons.kt` the line icons and `MedArt.kt` the twelve medicine drawings, which use Android's canvas so widgets and notifications draw the same thing.
- `MainActivity.kt` holds navigation, deep links from notifications, onboarding and the app lock.
- `app/src/debug/` holds the sample data receiver. It never ships in a release build.

## Rules that must not break

- **No internet.** The app never gets the INTERNET permission, directly or through a library. The manifest strips it, and `ManifestTest` fails if it comes back. Don't add analytics, crash reporting, or anything else that sends data.
- **Data stays on the phone.** Android backup and device transfer stay off. The app's own backup is a file the person saves where they choose.
- **Any kind of medicine.** Nothing may assume a pill. A dose is an amount of a `DoseUnit`, the drawing is one of the twelve `MedKind`s with `OTHER` for everything else, and supply is either counted or timed.
- **An answer always wins.** The app only marks a dose missed if nobody answered it. Never overwrite a taken or skipped dose.
- **The record doesn't rewrite itself.** Doses due before a schedule took effect (`Medicine.scheduleFrom`) never count, so adding or changing a medicine can't create misses in the past.
- **Times are the phone's.** Reminders follow the local clock, so they move with a time zone change, and times show in the phone's 12 or 24 hour format. Never hard-code either.
- **The as-needed guard warns, it doesn't block.** The person decides; the app makes the safe choice the easy one.
- **No dots.** No status dots, dot indicators or "·" separators anywhere in the UI. Use words, fills, outlines and bars.

## Database changes

Room exports each schema version to `app/schemas/`. Version 1 hasn't shipped, so until the first release it can still change in place. After that, when an entity changes:

1. Bump `version` in `@Database`, in `Database.kt`.
2. Write a migration that keeps every medicine and dose, and add it in `RexiDatabase.create`. Never use a destructive migration, because people would lose their history.
3. Commit the new schema JSON, and keep the old ones.
4. Add a test that upgrades real rows.

## Testing

- Logic in `core/` gets a JVM test. Database code gets a Robolectric test with an in-memory database. Robolectric tests use a plain `Application`, so Rexi's own startup work doesn't run under them.
- Test names are sentences in backticks, such as `` `an answer always wins over the clock` ``.
- On an emulator, load the sample data, then grant the reminder permissions with `adb shell pm grant top.yeetfactory.rexi android.permission.POST_NOTIFICATIONS` and `adb shell appops set top.yeetfactory.rexi SCHEDULE_EXACT_ALARM allow`.
- The README screenshots in `docs/screenshots/` come from the sample data, never from someone's real medicines, because those are health records. Take them with the status bar in Android's demo mode, showing 9:41 and a full battery. Each screen is 720 px wide with rounded corners. The banner is 1600 px wide, with three phones on Rexi's mint (`#BFE9DE`). Retake them when a screen they show changes.

## Code style

- Formatting and lint are enforced: ktlint in its `ktlint_official` style with compose-rules, strict Android Lint, and Kotlin warnings as errors. The settings are in `.editorconfig` and `app/build.gradle.kts`.
- Fix the code. Don't suppress a rule, add a baseline or loosen a setting.
- In Compose, every composable that draws takes `modifier: Modifier = Modifier` and applies it to its root. Callbacks are named in the present tense: `onSave`, not `onSaved`.
- Colours come from `LocalPalette.current`. Don't create other CompositionLocals.
- Comments explain why, in plain sentences.

## Writing copy

- Write short, plain sentences. Say what's known, and what isn't.
- Rexi records what the person logged. Never claim it knows a dose was swallowed, and never give medical advice.

## Commits and pull requests

- Use Conventional Commits, such as `feat:`, `fix:`, `docs:`, `refactor:`, `test:` and `chore:`. Branches use the same prefixes, such as `fix/missed-dose-time`.
- The commit body says what changed and why, in plain sentences.
- Never commit `keystore.properties`, `*.jks` or `local.properties`. Release builds read the signing key from `keystore.properties`.
- Releases go through the `rexi-release` skill in `.agents/skills/`.
