package de.robv.android.xposed;

import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;

/**
 * Functional unit-test fake of the Xposed bridge. The published api jar is a
 * throwing stub, so this replaces it on the test classpath and records calls so
 * tests can assert on them.
 */
public final class XposedBridge {

    public static final List<String> logs = new ArrayList<>();
    public static final List<Member> hookedMembers = new ArrayList<>();
    public static XC_MethodHook lastCallback;

    /** When set, {@link #hookMethod} throws it (to exercise failure handling). */
    public static RuntimeException hookError;

    private XposedBridge() {}

    public static void log(String text) {
        logs.add(text);
    }

    public static void log(Throwable t) {
        logs.add(String.valueOf(t));
    }

    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        if (hookError != null) {
            throw hookError;
        }
        hookedMembers.add(method);
        lastCallback = callback;
        return new XC_MethodHook.Unhook();
    }

    public static void reset() {
        logs.clear();
        hookedMembers.clear();
        lastCallback = null;
        hookError = null;
    }
}
