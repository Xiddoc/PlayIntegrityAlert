# The Xposed framework loads the entry class named in assets/xposed_init by
# reflection, so it (and anything it touches by name) must survive shrinking.
-keep class com.xiddoc.playintegrityalert.XposedEntry { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit

# isModuleActivated() is overwritten by the module at runtime; keep its name.
-keep class com.xiddoc.playintegrityalert.MainActivity {
    boolean isModuleActivated();
}

# Xposed API is provided by the framework — don't warn about the compileOnly refs.
-dontwarn de.robv.android.xposed.**
