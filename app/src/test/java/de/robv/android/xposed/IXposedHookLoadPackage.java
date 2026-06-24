package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Functional unit-test fake of the load-package hook interface. */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
