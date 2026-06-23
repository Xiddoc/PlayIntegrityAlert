package com.xiddoc.playintegrityalert

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.ConcurrentHashMap

/**
 * Installed once per hooked app process. Detects when the app asks Google Play
 * for a Play Integrity verdict.
 *
 * The Play Integrity / Play Core client libraries always reach the verdict
 * service by binding to Play Store (`com.android.vending`) via
 * [Context.bindService] with the integrity AIDL interface as the intent action
 * (legacy `IIntegrityService` and the Standard "Express" integrity service).
 * Hooking the bind — rather than the heavily obfuscated client classes — gives a
 * stable, version-independent signal that works no matter how the app shaded or
 * minified the library.
 */
object PlayIntegrityHook {

    /** Per-package throttle so a single request burst yields a single alert. */
    private const val DEBOUNCE_MS = 4_000L
    private val lastAlertAt = ConcurrentHashMap<String, Long>()

    fun install(packageName: String, classLoader: ClassLoader) {
        val contextImpl = runCatching {
            classLoader.loadClass("android.app.ContextImpl")
        }.getOrElse {
            XposedBridge.log("[${Constants.TAG}] ContextImpl unavailable in $packageName: $it")
            return
        }

        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                if (!isIntegrityBind(intent)) return
                val context = param.thisObject as? Context
                onIntegrityRequest(packageName, intent, context)
            }
        }

        // bindService has several overloads across API levels; hook every one
        // that takes an Intent so we catch whichever the client library uses.
        var hooked = 0
        contextImpl.declaredMethods
            .filter { it.name == "bindService" && it.parameterTypes.any { p -> p == Intent::class.java } }
            .forEach { method ->
                runCatching {
                    XposedBridge.hookMethod(method, callback)
                    hooked++
                }
            }

        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_INSTALLED} pkg=$packageName")
        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_READY} pkg=$packageName methods=$hooked")
    }

    /** True if [intent] is a bind to the Play Integrity verdict service. */
    private fun isIntegrityBind(intent: Intent): Boolean {
        val action = intent.action?.lowercase().orEmpty()
        if (Constants.INTEGRITY_ACTION_HINTS.any { action.contains(it) }) return true

        val component = intent.component?.className?.lowercase().orEmpty()
        return Constants.INTEGRITY_COMPONENT_HINTS.any { component.contains(it) }
    }

    private fun onIntegrityRequest(packageName: String, intent: Intent, context: Context?) {
        val now = System.currentTimeMillis()
        val previous = lastAlertAt.put(packageName, now)
        if (previous != null && now - previous < DEBOUNCE_MS) return

        val detail = intent.action ?: intent.component?.flattenToShortString() ?: "Play Integrity request"
        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_DETECTED} pkg=$packageName detail=$detail")

        if (context != null) {
            Notifier.notifyDetection(context, packageName, detail)
        }
    }
}
