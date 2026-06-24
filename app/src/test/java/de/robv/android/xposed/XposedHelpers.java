package de.robv.android.xposed;

import java.util.ArrayList;
import java.util.List;

/** Functional unit-test fake of {@code XposedHelpers}. */
public final class XposedHelpers {

    public static final List<String> hookedMethods = new ArrayList<>();

    /** When set, {@link #findAndHookMethod} throws it (to exercise failure handling). */
    public static RuntimeException error;

    private XposedHelpers() {}

    public static XC_MethodHook.Unhook findAndHookMethod(
            String className,
            ClassLoader classLoader,
            String methodName,
            Object... parameterTypesAndCallback) {
        if (error != null) {
            throw error;
        }
        hookedMethods.add(className + "#" + methodName);
        return new XC_MethodHook.Unhook();
    }

    public static void reset() {
        hookedMethods.clear();
        error = null;
    }
}
