package com.xiddoc.playintegrityalert

import android.app.AndroidAppHelper
import android.content.Context
import android.os.Bundle
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

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
 */
object IntegrityServiceHook {

    /** Per-caller throttle so one request burst yields one alert. */
    private const val DEBOUNCE_MS = 4_000L
    private val lastAlertAt = ConcurrentHashMap<String, Long>()

    private val packageNamePattern =
        Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+)+")

    fun install(classLoader: ClassLoader) {
        val callback = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val args = param.args ?: return
                if (!looksLikeRequest(args)) return
                val caller = extractCallerPackage(args) ?: return
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

    /** A request carries a caller package and/or a nonce; a response carries token/error. */
    private fun looksLikeRequest(args: Array<Any?>): Boolean {
        args.forEach { arg ->
            val bundle = arg as? Bundle ?: return@forEach
            if (bundle.containsKey("token") || bundle.containsKey("error")) return@forEach
            val hasPkg = Constants.CALLER_PACKAGE_KEYS.any { bundle.containsKey(it) }
            val hasNonce = runCatching {
                bundle.keySet().any { it.contains("nonce", ignoreCase = true) }
            }.getOrDefault(false)
            if (hasPkg || hasNonce) return true
        }
        return false
    }

    private fun extractCallerPackage(args: Array<Any?>): String? {
        args.forEach { arg ->
            val bundle = arg as? Bundle ?: return@forEach
            Constants.CALLER_PACKAGE_KEYS.forEach { key ->
                bundle.getString(key)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            // Fallback: any package-shaped string value in the request Bundle.
            runCatching {
                bundle.keySet().forEach { key ->
                    val value = bundle.getString(key) ?: return@forEach
                    if (packageNamePattern.matcher(value).matches()) return value
                }
            }
        }
        return null
    }

    private fun onIntegrityRequest(caller: String, serviceObject: Any?) {
        if (!WatchList.isWatched(caller)) return

        val now = System.currentTimeMillis()
        val previous = lastAlertAt.put(caller, now)
        if (previous != null && now - previous < DEBOUNCE_MS) return

        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_DETECTED} pkg=$caller")

        // The service object is itself a Context; fall back to the app context.
        val context = serviceObject as? Context ?: AndroidAppHelper.currentApplication()
        if (context != null) {
            Notifier.notifyDetection(context, caller, "Play Integrity verdict requested")
        }
    }
}
