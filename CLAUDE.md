# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

PIAlert is an Xposed / LSPosed module that **notifies you the moment an app asks
for a Play Integrity verdict**. It hooks the Play Store (Finsky) process only,
reads the requesting app's package out of the integrity-request `Bundle`, and —
if that package is on the user's watch-list — fires a notification and records
the detection. It only **observes**; it never alters the verdict.

## Commands

Standard Gradle Android build (JDK 17 + Android SDK; `compileSdk`/`targetSdk` 35,
`minSdk` 30 — Android 11):

```bash
./gradlew :app:assembleDebug                       # debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease                     # R8-minified, signed release APK → app/build/outputs/apk/release/
./gradlew :app:lintDebug                            # Android lint
./gradlew :app:testDebugUnitTest                    # run the JVM unit suite
./gradlew :app:jacocoTestReport                     # HTML coverage → app/build/reports/jacoco/
./gradlew :app:jacocoTestCoverageVerification       # enforce 100% line + branch (the gate)
./gradlew check                                      # runs tests + the coverage gate
```

Run a single test class or method:

```bash
./gradlew :app:testDebugUnitTest --tests "com.xiddoc.playintegrityalert.IntegrityRequestInspectorTest"
./gradlew :app:testDebugUnitTest --tests "*.AlertThrottleTest.debounces*"
```

## Coverage gate — non-negotiable

The build enforces **100% line and branch coverage of every hand-written class**;
only generated code (`R`, `BuildConfig`, `Manifest`) is excluded (see
`coverageExclusions` in `app/build.gradle.kts`). Any new or changed code must come
with tests that keep both counters at 1.0, or the build fails. This covers the
Xposed hook wiring and the UI Activities too — not just the pure logic.

The unit suite runs entirely on the host JVM (JUnit + Robolectric + MockK — no
device or emulator). To make the hook code run off-device, the suite ships
**functional fakes of the Xposed API** under `app/src/test/java/de/robv/...` and
`android.app.AndroidAppHelper`. These replace the published
`de.robv.android.xposed:api` jar, which is `compileOnly` in main and deliberately
kept *off* the test classpath (its real method bodies throw). When you add a hook
path that touches a new Xposed API, you must extend these fakes accordingly.

## Architecture — why it's shaped this way

The Play Integrity client libraries don't compute a verdict in-process; they hand
the request to **Google Play Store** (`com.android.vending`, "Finsky"), and the
caller's package travels *inside* that request. So the module injects into the
**Play Store process only** and watches the Finsky integrity services
(`Constants.INTEGRITY_SERVICE_CLASSES`). Hooking one process instead of every app
is far lighter, and because the caller package is in the request, that one process
still sees every app's request.

Runtime data flow (request → notification):

```
XposedEntry (loads only in com.android.vending)
  └─ IntegrityServiceHook        hooks the Finsky integrity service methods
       └─ IntegrityRequestInspector   PURE: is this a request? whose package?
            └─ WatchList (XSharedConfigSource)   is that package watched?
                 └─ AlertThrottle    per-caller debounce
                      └─ Notifier    explicit broadcast → our app's process
                           └─ DetectionReceiver   raises notification + DetectionStore
```

Key design seams to respect:

- **`IntegrityRequestInspector` is pure** — no Xposed dependency. The
  security-critical heuristics (request-vs-response discrimination via `token`/
  `error` keys, and caller extraction via `Constants.CALLER_PACKAGE_KEYS` plus a
  package-shaped-string fallback) live here precisely so they're fully JVM-testable.
  Keep new detection logic here, not in the hook.
- **Two caller-attribution paths.** The Bundle heuristics above cover the *classic*
  Play Integrity API, where the caller package rides inside the request `Bundle`.
  The *Standard/Express* API instead hands Finsky a Parcelable with no package, so
  `IntegrityServiceHook.callerFromBinder` falls back to the binder calling UID
  (`Binder.getCallingUid()`, always valid inside the transaction) and resolves it to
  a package via the Play Store process's `PackageManager` — filtering out system,
  Play Store, and our own UID. Without this, Standard-API requests are invisible.
- **Hook the returned binder, not just the service class.** The integrity *request*
  (`requestIntegrityToken` and friends) isn't a method on the `IntegrityService`
  class — Finsky returns an AIDL binder stub from `onBind` and dispatches the
  cross-process request onto *that* object. Hooking only the service's declared
  methods catches lifecycle/bind calls but never the request. So
  `IntegrityServiceHook.hookBinderResult` runs in the callback's `afterHookedMethod`:
  whenever a hooked service method returns an `IBinder`, it hooks that stub's methods
  too (once per class), which is where the request — and its caller UID — lands.
- **Diagnosability.** The liveness heartbeat fires on the *first* call to any hooked
  method (not only on a successful detection), so the app shows "watching" as soon as
  the hook is exercised in Play Store. Any hooked call we can't attribute is logged
  with its method name and argument shapes (`unattributed integrity call …`), so an
  undetected request can be diagnosed straight from the LSPosed log.
- **Cross-process config** — the watch-list is written app-side by `Config` into
  world-readable prefs and read inside the Play Store process via
  `XSharedConfigSource` (backed by `XSharedPreferences`), LSPosed's supported
  channel. It **fails safe**: if prefs can't be read, it watches all apps rather
  than going silent. `WatchList` reads through a swappable `Source` for testing.
- **The notification is raised by our app, not Finsky** — the hook sends an
  explicit, stopped-package-safe broadcast (`Constants.ACTION_DETECTED`) to
  `DetectionReceiver`, so the alert always carries our icon, channel, and
  `POST_NOTIFICATIONS` permission, independent of the Play Store process.
- **Injectable seams for determinism** — an injectable clock/throttle
  (`AlertThrottle`), the swappable watch-list `Source`, and a swappable background
  runner keep the time- and thread-dependent paths testable.
- **`Constants.LOG_*` markers** (`PIA_MODULE_LOADED`, `PIA_HOOK_INSTALLED`,
  `PIA_DETECTED`) are logged through `XposedBridge` so LSPosed logs and the e2e
  job can assert behaviour — don't rename them without updating `e2e.yml` /
  `scripts/`.

## Continuous integration

Three workflows under `.github/workflows/`:

- **`build.yml` — the gate (every push / PR).** Gradle build + the unit suite
  behind the 100% coverage gate + Android lint + uploads the debug APK. This is
  the enforcing gate.
- **`release.yml` — cut a release (every merge to `master`).** Autobumps the
  version (patch-bumps the latest `v*` git tag for `versionName`; the commit count
  is the monotonic `versionCode`, both injected into the build via the
  `VERSION_NAME` / `VERSION_CODE` Gradle properties), builds the **R8-minified,
  signed** release APK (`:app:assembleRelease`), then tags the commit and
  publishes a GitHub Release with the APK attached. Release signing uses the
  `PIA_KEYSTORE_BASE64` / `PIA_KEYSTORE_PASSWORD` / `PIA_KEY_ALIAS` /
  `PIA_KEY_PASSWORD` repo secrets; with none set it falls back to the debug key so
  the APK is still installable. The `release` build type enables `isMinifyEnabled`
  + `isShrinkResources`, so any new reflectively-referenced symbol must be kept in
  `proguard-rules.pro` or it will be stripped.
- **`e2e.yml` — real LSPosed boot (gated).** Uses
  [Xiddoc/Beetroot](https://github.com/Xiddoc/Beetroot) to boot a rooted
  Android 14 + LSPosed (Vector) instance (`e2e/beetroot-lsposed.yaml`), installs
  the module, and asserts the `PIA_MODULE_LOADED` marker appears in LSPosed's
  log. Runs only on the **`e2e`** PR label, manual dispatch, or nightly schedule.
  It's `continue-on-error` (informational) because current GitHub-hosted runner
  kernels no longer ship the `binder_linux` module Beetroot's "Option A" needs.

## Project layout

```
app/src/main/java/com/xiddoc/playintegrityalert/
  XposedEntry.kt                # module entry; installs the hook in the Play Store process
  IntegrityServiceHook.kt       # hooks Finsky integrity services; thin Xposed wiring
  IntegrityRequestInspector.kt  # pure request/response heuristic + caller extraction
  AlertThrottle.kt              # per-caller debounce (injectable clock)
  WatchList.kt                  # watch-list decision; reads via a swappable Source
  XSharedConfigSource.kt        # default Source backed by XSharedPreferences
  Config.kt                     # app-side watch-list reader/writer (world-readable prefs)
  Notifier.kt                   # bridges a detection to our app via broadcast
  DetectionReceiver.kt          # raises the notification + records history
  DetectionStore.kt             # persistent ring buffer of recent detections
  Constants.kt                  # shared package/key/marker constants
  MainActivity.kt               # status + watch-all toggle + history + test button
  AppPickerActivity.kt          # per-app watch-list picker
  AlertApp.kt                   # notification channel
app/src/test/java/com/xiddoc/playintegrityalert/   # JVM unit suite (100% gate)
app/src/test/java/de/robv/... , android/app/AndroidAppHelper.java  # Xposed API fakes
app/src/main/assets/xposed_init    # names the entry class (classic Xposed contract)
scripts/                           # e2e driver + boot-wait helper
e2e/beetroot-lsposed.yaml          # Beetroot instance config (Android 14 + Vector)
```

## Limitations

- Detection runs in the Play Store process, so Play Store must be in the module's
  scope, and real verdict requests require Google Play services present.
- The module only observes — it never alters the verdict.
```
