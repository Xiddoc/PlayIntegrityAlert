package de.robv.android.xposed;

import java.io.File;
import java.util.Set;

/**
 * Functional unit-test fake of {@code XSharedPreferences} with test-controllable
 * backing values, so the {@code XSharedConfigSource} that reads it can run on the JVM.
 */
public class XSharedPreferences {

    public static boolean watchAll = true;
    public static Set<String> watched = null;
    public static boolean reloadShouldThrow = false;
    public static int reloadCount = 0;

    public XSharedPreferences(String packageName, String prefsName) {}

    public XSharedPreferences(String packageName) {}

    public XSharedPreferences(File file) {}

    public void reload() {
        reloadCount++;
        if (reloadShouldThrow) {
            throw new RuntimeException("reload failed");
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        return watchAll;
    }

    public Set<String> getStringSet(String key, Set<String> defValue) {
        return watched;
    }

    public static void reset() {
        watchAll = true;
        watched = null;
        reloadShouldThrow = false;
        reloadCount = 0;
    }
}
