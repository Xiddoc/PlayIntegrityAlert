# Play Integrity Alert

An Xposed / LSPosed module that **notifies you the moment any app you've enabled
in its scope asks Google Play for a Play Integrity verdict.**

Add the apps you want to watch to the module's LSPosed scope, and whenever one of
them calls the Play Integrity API you get a notification — with the app's name —
plus an in-app history of every detection.

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Play Integrity Alert icon">
</p>

## How it works

The Play Integrity / Play Core client libraries don't compute a verdict
in-process — they reach the verdict service by **binding to Google Play Store**
(`com.android.vending`) over IPC. The bind `Intent` carries the integrity AIDL
interface as its action (e.g.
`com.google.android.play.core.integrity.protocol.IIntegrityService`, and the
Standard/Express integrity variant).

So, inside **each app the user scopes the module to**, Play Integrity Alert hooks
`android.app.ContextImpl.bindService` and flags any bind whose action or target
component refers to *integrity*:

```
app (scoped)  ──bindService(IIntegrityService)──▶  Play Store
      │
      └─▶ ContextImpl.bindService hook  ──▶  notification + history
```

Hooking the bind — rather than the heavily obfuscated client classes — is a
stable, version-independent signal that survives the app shading/minifying the
library, and it fires from the **caller's** process, which is exactly what
"an app in the scope used Play Integrity" means.

The notification is raised by *this* app (via an explicit, stopped-package-safe
broadcast to its receiver), so it always carries our icon, our notification
channel, and our `POST_NOTIFICATIONS` permission — independent of whether the
watched app holds it.

> **Research credit & inspiration:**
> [ElDavoo/PlayIntegrityBreak](https://github.com/ElDavoo/PlayIntegrityBreak)
> demonstrates the *server-side* of the same flow — hooking the Finsky
> `IntegrityService` classes (`com.google.android.finsky.integrityservice.*`)
> inside the Play Store process and reading the caller package out of the request
> `Bundle`. That approach catches every caller system-wide; this module instead
> watches the *client* side so detection is naturally scoped to the apps you
> pick in LSPosed.

## Usage

1. Build/install the APK (below) and enable **Play Integrity Alert** in LSPosed.
2. In the module's **Scope**, tick every app you want to watch. Also tick
   **Play Integrity Alert itself** — the in-app status then reads *Module
   active ✓*.
3. Force-stop the watched apps (or reboot) so the hook loads into them.
4. When a watched app requests a Play Integrity verdict you get a notification,
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

Builds the module with Gradle, runs Android lint, and uploads the debug APK as a
build artifact.

### `e2e.yml` — real LSPosed boot (gated)

Uses [**Xiddoc/Beetroot**](https://github.com/Xiddoc/Beetroot) to prove the
module actually loads and installs its hooks under a *real* LSPosed framework:

1. Builds the module APK.
2. Loads the host `binder` kernel module (Beetroot's
   [Option A](https://github.com/Xiddoc/Beetroot/blob/master/docs/guides/running-in-ci.md)
   path on GitHub-hosted runners).
3. Boots a rooted **Android 14 + LSPosed (Vector)** redroid instance from
   [`e2e/beetroot-lsposed.yaml`](e2e/beetroot-lsposed.yaml) (Vector flashed
   declaratively as a Zygisk module).
4. Installs the module, enables it in LSPosed's scope for a target app, reboots,
   launches the target, and asserts the module's `PIA_HOOK_INSTALLED` marker
   appears in LSPosed's module log — the same technique as Beetroot's own
   `lsposed-hook-e2e.sh`.

Because real boots are slow, `e2e.yml` runs only on the **`e2e`** PR label,
manual dispatch, or the nightly schedule — mirroring Beetroot's own e2e gating.

## Project layout

```
app/src/main/java/com/xiddoc/playintegrityalert/
  XposedEntry.kt        # module entry; installs the hook per scoped app
  PlayIntegrityHook.kt  # hooks ContextImpl.bindService, detects integrity binds
  Notifier.kt           # bridges a detection to our app via broadcast
  DetectionReceiver.kt  # raises the notification + records history
  DetectionStore.kt     # persistent ring buffer of recent detections
  MainActivity.kt       # status + instructions + history + test button
  AlertApp.kt           # notification channel
app/src/main/assets/xposed_init   # names the entry class (classic Xposed contract)
scripts/                # e2e driver + boot-wait helper
e2e/beetroot-lsposed.yaml         # Beetroot instance config (Android 14 + Vector)
.github/workflows/      # build.yml (gate) + e2e.yml (Beetroot)
```

## Limitations

- Detection is scoped to apps you enable in LSPosed. To catch *every* caller
  system-wide, hook the Play Store process instead (see PlayIntegrityBreak).
- This module only **observes** — it never alters the verdict.

## License

See [LICENSE](LICENSE).
