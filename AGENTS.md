# Agent and contributor instructions

bf-12c is a personal HP-12C-inspired RPN calculator for Android, sideloaded
only. Read `README.md` for the feature overview and keep it in sync with any
behavior you change.

## Latest-only platform policy

Like Blauncher, this project supports **only the latest stable everything**:

- `minSdk`, `targetSdk` and `compileSdk` are the latest stable API level, all
  equal (`app/build.gradle.kts`). Bump all three together.
- No `Build.VERSION.SDK_INT` checks, no compat shims for older devices.
- AGP, Kotlin, Compose BOM and libraries (`gradle/libs.versions.toml`) and
  Gradle (`gradle/wrapper/gradle-wrapper.properties`, checksum-pinned) track
  the latest stable releases. Dependabot keeps them current.

## Layout

- `app/src/main/java/com/bradflaugher/bf12c/engine/` — the calculator. Pure
  Kotlin, no Android imports, fully unit-testable.
  - `Calculator.kt` — stack, entry, prefixes (f/g/STO/RCL/GTO), every key's
    function, program mode, persistence (`save`/`restore`).
  - `Key.kt` — the 39 keys, HP keycodes, legends and the 4×10 layout.
  - `BigMath.kt` — 34-digit exp/ln/pow/sqrt/n!; errors are `CalcError(code)`
    using the 12c's `Error n` numbering.
  - `Finance.kt`, `Dates.kt` — TVM, cash flows, amortization, depreciation,
    bonds, calendar math.
  - `ProgramParser.kt` — merges program keystrokes into lines, keycodes and
    mnemonics.
  - `Format.kt` — FIX / SCI / ALL display formatting.
- `CalcViewModel.kt` — owns the engine on a single background thread,
  runs programs, persists to SharedPreferences.
- `ui/` — Compose: `Keyboard.kt` (landscape 4×10 and portrait folded
  layouts), `DisplayPanel.kt` (CRT display), `CalculatorScreen.kt`
  (screen, brand plate, ON-key system menu), `FitText.kt` (deterministic
  one-line text sizing; use it instead of `TextAutoSize`), `Theme.kt`.
- `app/src/test/` — JVM unit tests. `engine/Keystrokes.kt` is the shared
  keystroke DSL; `HandbookTest` (owner's-handbook examples),
  `CalculatorTest` (keys, rules, regressions), `PropertyTest` (seeded
  identities and the keystroke fuzzer), `FormatTest`, `ProgramParserTest`,
  and `InvariantsTest` (enforces the invariants below).
- `tools/icon/gen_icon.py` — generates the adaptive launcher icon
  (`res/drawable/ic_launcher_*.xml`). Edit the script, not the XML.

## Behavioral rules

- Match the real HP-12C unless there is a deliberate, documented modern
  extension. Current extensions: 34 digits, `f EEX` = ALL display mode,
  `g −` = backspace (display swipe too; deletes the line in program mode),
  copy/paste, phosphor themes, 99 program lines, 20 cash flows, `f f` /
  `g g` cancels the prefix.
- Financial keys store when a number was just keyed or computed, and solve
  when pressed right after another financial key — the 12c rule.
- Keep the engine pure: all Android code lives outside `engine/`.
- Every engine change gets a test in `app/src/test/.../CalculatorTest.kt`
  (scripts are typed with the tiny keystroke DSL in `Keystrokes.kt`).
- Every stored result is rounded and range-checked (`fit`) before any
  register is written, so an error never leaves the stack half-updated.
- No keystroke may stall the engine: loops over user-sized counts are capped
  (AMORT, DB) or replaced by closed forms (SOYD). The fuzzer checks this.

## Invariants

- **No permissions.** No `INTERNET`, no backups (`allowBackup=false`, empty
  extraction rules).
- CI actions stay pinned to commit SHAs.

## Build, test, release

```sh
./gradlew lint test assembleDebug
```

`.github/workflows/ci.yml` runs the unit tests and lint as
separate checks on every pull request and push to `main`.

Every push to `main` builds a signed APK and publishes it as the single
date-labeled GitHub release `vYYYY.MM.DD.<run>`, deleting all older
releases. Pull requests build unsigned and publish nothing. Keep `main` green.

## Visual checks

A headless API 37 emulator can live in the gitignored `.emu/` directory:
unzip `emulator-linux_x64-*.zip` into `.emu/sdk/` and the
`sys-img/google_apis/x86_64-37.0_*.zip` image into
`.emu/sdk/system-images/android-37.0/google_apis/`, write an AVD under
`.emu/avd/`, then run `emulator -avd <name> -no-window` with
`ANDROID_SDK_ROOT`, `ANDROID_AVD_HOME` and `ANDROID_USER_HOME` pointing into
`.emu/`. Drive it with `adb shell input tap x y` and capture with
`adb exec-out screencap -p > shot.png`. Look at both orientations
(`adb shell settings put system user_rotation 0|1`).
