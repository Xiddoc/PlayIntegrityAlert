package com.xiddoc.playintegrityalert

import android.app.AndroidAppHelper
import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge

/**
 * Installed once, inside the Play Store (`com.android.vending`) process. Hooks the
 * Finsky integrity services and reports the requesting app whenever one asks for a
 * Play Integrity verdict.
 *
 * Hooking here — rather than in every watched app — means a single injected
 * process observes every app's request (the caller package travels inside the
 * request Bundle), which is markedly lighter on battery than injecting the module
 * into each scoped app. The approach mirrors ElDavoo/PlayIntegrityBreak, trimmed
 * to detection only: we never alter the verdict.
 *
 * This object is the thin Xposed-runtime wiring; the decision logic lives in
 * [IntegrityRequestInspector], [AlertThrottle] and [WatchList], which are unit
 * tested. The hook plumbing itself is covered by the e2e.
 */
object IntegrityServiceHook {

    internal var throttle = AlertThrottle()

    /** Whether we've already told the app the hook is alive (once per process). */
    internal var reportedAlive = false

    fun install(classLoader: ClassLoader) {
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val caller = IntegrityRequestInspector.callerPackage(param.args) ?: return
                onIntegrityRequest(caller, param.thisObject)
            }
        }

        var hooked = 0
        for (className in Constants.INTEGRITY_SERVICE_CLASSES) {
            val clazz = runCatching { classLoader.loadClass(className) }.getOrNull() ?: continue
            clazz.declaredMethods.forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(method, callback)
                    hooked++
                }
            }
        }
        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_INSTALLED} pkg=${Constants.VENDING_PACKAGE} methods=$hooked")
    }

    internal fun onIntegrityRequest(caller: String, serviceObject: Any?) {
        // The service object is itself a Context; fall back to the app context.
        val context = serviceObject as? Context ?: AndroidAppHelper.currentApplication()

        // A request reaching us proves the hook is live in Play Store: tell the app
        // once so its status can read "watching" without our app needing scope.
        reportAliveOnce(context)

        if (!WatchList.isWatched(caller)) return
        if (!throttle.allow(caller)) return

        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_DETECTED} pkg=$caller")

        if (context != null) {
            Notifier.notifyDetection(context, caller, "Play Integrity verdict requested")
        }
    }

    private fun reportAliveOnce(context: Context?) {
        if (reportedAlive || context == null) return
        reportedAlive = true
        Notifier.reportHookAlive(context)
    }
}
