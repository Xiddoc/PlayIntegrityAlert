package com.xiddoc.playintegrityalert

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Module entry point, named in `assets/xposed_init`.
 *
 * For every app the user enables in the LSPosed scope, [PlayIntegrityHook] is
 * installed in that app's own process so a Play Integrity request is detected
 * from the caller's side. The module's own package is special-cased to flip the
 * in-app "module active" indicator.
 */
@Suppress("unused")
class XposedEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == Constants.OWN_PACKAGE) {
            markSelfActivated(lpparam)
            return
        }

        runCatching {
            PlayIntegrityHook.install(lpparam.packageName, lpparam.classLoader)
        }.onFailure {
            XposedBridge.log("[${Constants.TAG}] failed to hook ${lpparam.packageName}: $it")
        }
    }

    /** Make [MainActivity.isModuleActivated] return true inside our own UI. */
    private fun markSelfActivated(lpparam: XC_LoadPackage.LoadPackageParam) {
        runCatching {
            XposedHelpers.findAndHookMethod(
                "$OWN_ACTIVITY",
                lpparam.classLoader,
                "isModuleActivated",
                XC_MethodReplacement.returnConstant(true),
            )
        }
    }

    private companion object {
        const val OWN_ACTIVITY = "com.xiddoc.playintegrityalert.MainActivity"
    }
}
