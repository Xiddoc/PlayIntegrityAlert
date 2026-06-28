package com.xiddoc.playintegrityalert

import android.app.AndroidAppHelper
import android.content.Context
import android.os.Binder
import android.os.Bundle
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.lang.reflect.Member

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
                handleHookedCall(param.method, param.args, param.thisObject)
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

    /**
     * Entry point for every hooked integrity-service method call.
     *
     * Reaching here at all proves the hook is live inside Play Store, so we flip the
     * app's "watching" heartbeat on the first call — independent of whether a caller
     * can be attributed. We then try the Bundle path (classic API) and the binder-UID
     * path (Standard/Express API); if neither yields a caller we log the call's shape
     * so an undetected request can be diagnosed straight from the LSPosed log.
     */
    internal fun handleHookedCall(method: Member?, args: Array<Any?>?, serviceObject: Any?) {
        val context = contextFrom(serviceObject)
        reportAliveOnce(context)

        val caller = IntegrityRequestInspector.callerPackage(args) ?: callerFromBinder(serviceObject)
        if (caller == null) {
            val name = if (method == null) "?" else method.name
            XposedBridge.log(
                "[${Constants.TAG}] unattributed integrity call $name args=${describeArgs(args)}",
            )
            return
        }
        onIntegrityRequest(caller, context)
    }

    /** Compact, log-safe description of a hooked call's arguments, for diagnosis. */
    internal fun describeArgs(args: Array<Any?>?): String {
        if (args == null) return "null"
        return args.joinToString(prefix = "[", postfix = "]") { arg ->
            when (arg) {
                null -> "null"
                is Bundle -> "Bundle{${runCatching { arg.keySet().joinToString(",") }.getOrDefault("?")}}"
                else -> arg.javaClass.name
            }
        }
    }

    /**
     * Caller of an integrity request whose package isn't in a Bundle (the Standard/
     * Express Integrity API hands Finsky a Parcelable instead). Inside the binder
     * transaction the calling app's UID is always available, so we resolve it to a
     * package via the Play Store process's [android.content.pm.PackageManager].
     * Returns null for system/host-process callers or when no app can be resolved.
     */
    internal fun callerFromBinder(serviceObject: Any?): String? {
        val callingUid = Binder.getCallingUid()
        if (!IntegrityRequestInspector.isExternalAppCaller(callingUid, Process.myUid())) return null
        val context = contextFrom(serviceObject) ?: return null
        val pkg = runCatching {
            context.packageManager.getPackagesForUid(callingUid)?.firstOrNull()
        }.getOrNull() ?: return null
        // Never attribute to Play Store or ourselves — those aren't the requesting app.
        if (pkg == Constants.VENDING_PACKAGE || pkg == Constants.OWN_PACKAGE) return null
        return pkg
    }

    internal fun onIntegrityRequest(caller: String, context: Context?) {
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

    /** The hooked service object is itself a Context; fall back to the app context. */
    private fun contextFrom(serviceObject: Any?): Context? =
        serviceObject as? Context ?: AndroidAppHelper.currentApplication()
}
