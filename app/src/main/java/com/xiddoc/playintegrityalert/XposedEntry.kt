package com.xiddoc.playintegrityalert

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Module entry point, named in `assets/xposed_init`.
 *
 * Detection is done entirely inside the Play Store (`com.android.vending`)
 * process: [IntegrityServiceHook] watches the Finsky integrity services and reads
 * the requesting app's package out of the request — so one injected process sees
 * every app's Play Integrity call. The user picks *which* of those apps to be
 * alerted about in the app UI (see [WatchList]). The module's own package is
 * special-cased to flip the in-app "module loaded" indicator.
 *
 * Scope it to Play Store. Our own app doesn't need ticking — LSPosed can't scope a
 * module to itself via the UI, but it auto-scopes a legacy module to itself, which
 * is when [markSelfActivated] runs.
 */
@Suppress("unused")
class XposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Proves the module's code runs inside a scoped process (used by the e2e).
        XposedBridge.log("[${Constants.TAG}] ${Constants.LOG_MODULE_LOADED} pkg=${lpparam.packageName}")

        when (lpparam.packageName) {
            Constants.OWN_PACKAGE -> markSelfActivated(lpparam)
            Constants.VENDING_PACKAGE -> runCatching {
                IntegrityServiceHook.install(lpparam.classLoader)
            }.onFailure {
                XposedBridge.log("[${Constants.TAG}] failed to install integrity hook: $it")
            }
        }
    }

    /** Make [MainActivity.isModuleActivated] return true inside our own UI. */
    private fun markSelfActivated(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                "com.xiddoc.playintegrityalert.MainActivity",
                lpparam.classLoader,
                "isModuleActivated",
                XC_MethodReplacement.returnConstant(true),
            )
        }
    }
}
