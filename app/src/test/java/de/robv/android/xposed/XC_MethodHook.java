package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Functional unit-test fake of {@code XC_MethodHook}. Mirrors the public shape the
 * module compiles against (no-arg + priority constructors, the protected
 * before/after callbacks, and the {@code MethodHookParam}/{@code Unhook} nested
 * types) with benign bodies, plus a public helper so tests can invoke the
 * protected callback from outside the package.
 */
public class XC_MethodHook {

    public XC_MethodHook() {}

    public XC_MethodHook(int priority) {}

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    /** Test-only bridge to the protected callback. */
    public final void callBeforeHookedMethod(MethodHookParam param) throws Throwable {
        beforeHookedMethod(param);
    }

    /** Test-only bridge to the protected callback. */
    public final void callAfterHookedMethod(MethodHookParam param) throws Throwable {
        afterHookedMethod(param);
    }

    public static final class MethodHookParam {
        public Object[] args;
        public Member method;
        public Object thisObject;
        // The real API exposes result via accessors (setting it alters behaviour),
        // unlike the public fields above, so mirror that shape.
        private Object result;

        public MethodHookParam() {}

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }
    }

    public static class Unhook {
        public void unhook() {}
    }
}
