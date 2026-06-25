# Play Integrity Alert

<p align="center">
  <a href="https://github.com/Xiddoc/PlayIntegrityAlert/releases/latest">
    <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Play Integrity Alert icon — download the latest release">
  </a>
</p>

PIAlert is an Xposed / LSPosed module that **notifies you the moment an app asks for a Play Integrity verdict.**

Scope the module to Google Play Store, choose which apps you care about (or watch
them all), and whenever one of them calls the Play Integrity API you get a
notification — with the app's name — plus an in-app history of every detection.

This module behaves just like the [GrapheneOS Play Integrity alert](https://x.com/GrapheneOS/status/1877790719009529972) feature, and is inspired by it.

## Download

Grab the latest signed APK from the [**Releases**](https://github.com/Xiddoc/PlayIntegrityAlert/releases/latest) page — then enable it in LSPosed (see [Usage](#usage)).

## Usage

1. Install the APK (from [Releases](https://github.com/Xiddoc/PlayIntegrityAlert/releases/latest)) and **enable Play Integrity Alert** in LSPosed.
2. Open the module's **Scope** and make sure **Google Play Store** (`com.android.vending`)
   is ticked — that's the only app you need.
   - LSPosed **pre-selects** the module's recommended scope by default when you
     enable it, and that scope is now just Google Play Store, so it's usually
     already ticked. If it isn't — e.g. you enabled the module on an older build, or
     changed the scope yourself — just tick Google Play Store manually.
   - **Don't see Google Play Store in the list?** It's a *system app*, and LSPosed
     hides system apps by default. Tap the **⋮ menu** on the scope screen and enable
     **Show system apps** (some builds call it *Show system applications*), then
     pull-to-refresh — Google Play Store will appear so you can tick it.
   - You **can't** tick *Play Integrity Alert itself* — LSPosed never lets a module
     scope itself, and earlier builds that listed this app in the recommended scope
     could stop LSPosed pre-selecting Play Store. You don't need it: LSPosed
     auto-scopes a *legacy* module (which this is) to its own process, and that's
     what drives the in-app status check.
3. **Restart Play Store** so the hook loads into it: tap **Restart Play Store** in
   the app. **With root** (Magisk/KernelSU) it force-stops Play Store for you via
   `libsu`; **without root** it opens Play Store's *App info* so you can tap *Force
   stop* yourself. Either way, the module only loads into Play Store when its process
   (re)starts — a reboot also works.
4. Back in the app, keep **Watch all apps** on, or turn it off and **Choose apps…**.
5. When a watched app requests a Play Integrity verdict you get a notification, and
   the event is added to the in-app history.

### Checking it's active

The status line at the top of the app tells you exactly where you are:

- **Module not active** — the module isn't loaded. Enable it in LSPosed.
- **Module loaded ✓** — the module is running inside this app (so LSPosed has it
  enabled — it auto-scopes a legacy module to itself), but Play Store hasn't made an
  Integrity request through the hook yet. Make sure Google Play Store is ticked in
  the scope and restart it.
- **Watching Google Play Store ✓** — the module is loaded *and* the hook has actually
  run inside the Play Store process. That second signal (a one-time **heartbeat** the
  hook sends on its first real Integrity request) is the proof the Play Store hook is
  live, and it doesn't depend on this app being ticked in any scope. Disable the
  module later and the status drops back — it won't show a stale tick.

**Send test notification** only exercises the notification path inside this app; it
does *not* touch the heartbeat or prove the Play Store hook is live. The
**Watching…** status does.

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
process. The same broadcast channel carries a one-time **heartbeat** the moment the
hook first runs inside Play Store, which is what lights up the *Watching…* status.

## License

See [LICENSE](LICENSE).
