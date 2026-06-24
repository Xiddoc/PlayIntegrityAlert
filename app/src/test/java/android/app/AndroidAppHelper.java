package android.app;

/**
 * Functional unit-test fake of Xposed's {@code android.app.AndroidAppHelper}. The
 * real class is injected by the framework at runtime; here a test-settable field
 * stands in for the current application.
 */
public final class AndroidAppHelper {

    public static Application currentApplication;

    private AndroidAppHelper() {}

    public static Application currentApplication() {
        return currentApplication;
    }

    public static void reset() {
        currentApplication = null;
    }
}
