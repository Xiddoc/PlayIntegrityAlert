# Play Integrity Alert

An Xposed / LSPosed module that **notifies you the moment an app asks Google Play
for a Play Integrity verdict.**

Scope the module to Google Play Store, choose which apps you care about (or watch
them all), and whenever one of them calls the Play Integrity API you get a
notification — with the app's name — plus an in-app history of every detection.

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Play Integrity Alert icon">
</p>

## How it works

The Play Integrity / Play Core client libraries don't compute a verdict
in-process — they hand the request to **Google Play Store** (`com.android.vending`,
"Finsky"), which runs the integrity services that produce the verdict. The
requesting app's package name travels *inside* that request.

So Play Integrity Alert injects a hook into the **Play Store process only** and
watches the Finsky integrity services
(`com.google.android.finsky.integrityservice.*` and the Express variant). When a
request comes in, it reads the caller's package out of the request `Bundle`:

```
any app  ──requestIntegrityToken()──▶  Play Store (Finsky)
                                          │  IntegrityService.* hook
                                          ▼
                              caller pkg ∈ watch-list?  ──▶  notification + history
```

This is the approach demonstrated by
[**ElDavoo/PlayIntegrityBreak**](https://github.com/ElDavoo/PlayIntegrityBreak)
(trimmed here to *detection only* — the verdict is never altered).

### Why hook Finsky instead of each app

Hooking one process (Play Store) instead of injecting into every watched app is
much lighter on battery and memory: the module loads into a single process, and
its hook only runs on real integrity calls rather than filtering unrelated work
in every app. Because the caller package is in the request, one process still
sees **every** app's request.

To keep the per-app selection that LSPosed scoping would otherwise give, the app
maintains a **watch-list**: keep *Watch all apps* on, or turn it off and pick
specific apps. The list is shared into the Play Store process via
`XSharedPreferences` — LSPosed's supported channel for a module to read its own
app's preferences across processes. (It fails safe: if the prefs can't be read,
it watches all apps rather than going silent.)

The notification is raised by *this* app (via an explicit, stopped-package-safe
broadcast to its receiver), so it always carries our icon, our notification
channel, and our `POST_NOTIFICATIONS` permission — independent of the Play Store
process.

## Usage

1. Build/install the APK (below) and enable **Play Integrity Alert** in LSPosed.
2. In the module's **Scope**, tick **Google Play Store**. Also tick **Play
   Integrity Alert itself** — the in-app status then reads *Module active ✓*.
   (LSPosed suggests exactly these via the module's default scope.)
3. Reboot (or force-stop Play Store) so the hook loads into it.
4. In the app, keep **Watch all apps** on, or turn it off and **Choose apps to
   watch…**.
5. When a watched app requests a Play Integrity verdict you get a notification,
   and the event is added to the in-app history.

Use **Send test notification** in the app to verify the notification path end to
end.

## Building

Standard Gradle Android build — JDK 17 and the Android SDK:

```bash
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

- `compileSdk` 35, `minSdk` 24, `targetSdk` 35.
- The Xposed API (`de.robv.android.xposed:api:82`) is a **`compileOnly`**
  dependency — the framework provides the real implementation at runtime, and it
  is never bundled into the APK.
- `xposedminversion` is `100` (the modern LSPosed Xposed-API line). It loads on
  any framework reporting API ≥ 100, including the v102+ requested for this
  project.

## Continuous integration

CI is split into two GitHub Actions workflows.

### `build.yml` — the gate (every push / PR)

Builds the module with Gradle, runs the unit suite behind a **100% coverage
gate** (line + branch, every hand-written class), runs Android lint, and uploads
the debug APK as a build artifact. Any coverage regression fails the build.

### Tests & coverage

The detection logic is verified by a host-side JVM suite (JUnit + Robolectric +
MockK — no device or emulator needed):

```bash
./gradlew :app:testDebugUnitTest                  # run the suite
./gradlew :app:jacocoTestReport                   # HTML report → app/build/reports/jacoco/…
./gradlew :app:jacocoTestCoverageVerification     # enforce 100% line + branch
```

The gate enforces **100% line and branch coverage of every hand-written class**
(only generated code — `R`, `BuildConfig`, `Manifest` — is excluded). That spans
the safety-critical logic (the request/response heuristic and caller extraction in
`IntegrityRequestInspector`, the per-caller debounce in `AlertThrottle`, the
watch-list decision in `WatchList`, config read/fallback in `Config`, detection
serialization in `DetectionStore`, the broadcast bridge in `Notifier`, the alert
receiver in `DetectionReceiver`) *and* the framework wiring — the Xposed
entry/hook (`XposedEntry`, `IntegrityServiceHook`), the `XSharedPreferences`
binding (`XSharedConfigSource`), and the UI Activities.

To run the hook code on a plain JVM, the suite provides small **functional fakes
of the Xposed API** (under `src/test/java/de/robv/...` and `android.app.AndroidAppHelper`)
in place of the published `compileOnly` stub jar, whose method bodies throw. A few
behaviour-preserving seams (an injectable clock/throttle, a swappable watch-list
`Source`, and a swappable background runner) keep the time- and thread-dependent
paths deterministic.

### `e2e.yml` — real LSPosed boot (gated)

Uses [**Xiddoc/Beetroot**](https://github.com/Xiddoc/Beetroot) to prove the
module actually loads under a *real* LSPosed framework:

1. Builds the module APK.
2. Loads the host `binder` kernel module (Beetroot's
   [Option A](https://github.com/Xiddoc/Beetroot/blob/master/docs/guides/running-in-ci.md)
   path on GitHub-hosted runners).
3. Boots a rooted **Android 14 + LSPosed (Vector)** redroid instance from
   [`e2e/beetroot-lsposed.yaml`](e2e/beetroot-lsposed.yaml) (Vector flashed
   declaratively as a Zygisk module).
4. Installs the module, enables it in LSPosed's scope for a target app, reboots,
   launches the target, and asserts the module's `PIA_MODULE_LOADED` marker
   appears in LSPosed's module log — the same technique as Beetroot's own
   `lsposed-hook-e2e.sh`. (Asserting a real verdict detection would additionally
   need GApps + a Play Integrity caller; the gate proves the module loads and
   runs under LSPosed.)

Because real boots are slow, `e2e.yml` runs only on the **`e2e`** PR label,
manual dispatch, or the nightly schedule — mirroring Beetroot's own e2e gating.

> **Best-effort on GitHub-hosted runners.** Beetroot's CI "Option A" loads the
> host `binder_linux` kernel module, but current GitHub-hosted runner kernels no
> longer ship it, so the boot can't come up on the default runner. The job is
> marked `continue-on-error` (informational, never blocks the PR) until the
> binderless VM backend is wired up. `build.yml` is the enforcing gate.

## Project layout

```
app/src/main/java/com/xiddoc/playintegrityalert/
  XposedEntry.kt             # module entry; installs the hook in the Play Store process
  IntegrityServiceHook.kt    # hooks Finsky integrity services; thin Xposed wiring
  IntegrityRequestInspector.kt # pure request/response heuristic + caller extraction
  AlertThrottle.kt           # per-caller debounce (injectable clock)
  WatchList.kt               # watch-list decision; reads via a swappable Source
  XSharedConfigSource.kt     # default Source backed by XSharedPreferences
  Config.kt                  # app-side watch-list reader/writer (world-readable prefs)
  Notifier.kt                # bridges a detection to our app via broadcast
  DetectionReceiver.kt       # raises the notification + records history
  DetectionStore.kt          # persistent ring buffer of recent detections
  MainActivity.kt            # status + watch-all toggle + history + test button
  AppPickerActivity.kt       # per-app watch-list picker
  AlertApp.kt                # notification channel
app/src/test/java/com/xiddoc/playintegrityalert/  # JVM unit suite (100% gate)
app/src/main/assets/xposed_init   # names the entry class (classic Xposed contract)
scripts/                # e2e driver + boot-wait helper
e2e/beetroot-lsposed.yaml         # Beetroot instance config (Android 14 + Vector)
.github/workflows/      # build.yml (gate) + e2e.yml (Beetroot)
```

## Limitations

- Detection runs in the Play Store process, so Play Store must be in the module's
  scope. Real verdict requests require Google Play services to be present.
- This module only **observes** — it never alters the verdict.

## License

See [LICENSE](LICENSE).
