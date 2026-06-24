# Play Integrity Alert

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Play Integrity Alert icon">
</p>

PIAlert is an Xposed / LSPosed module that **notifies you the moment an app asks for a Play Integrity verdict.**

Scope the module to Google Play Store, choose which apps you care about (or watch
them all), and whenever one of them calls the Play Integrity API you get a
notification — with the app's name — plus an in-app history of every detection.

This module behaves just like the [GrapheneOS Play Integrity alert](https://x.com/GrapheneOS/status/1877790719009529972) feature, and is inspired by it.

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

## License

See [LICENSE](LICENSE).
