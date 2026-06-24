package de.robv.android.xposed;

/** Functional unit-test fake of {@code XC_MethodReplacement}. */
public abstract class XC_MethodReplacement extends XC_MethodHook {

    public XC_MethodReplacement() {}

    public XC_MethodReplacement(int priority) {}

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    public static XC_MethodReplacement returnConstant(final Object result) {
        return new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                return result;
            }
        };
    }
}
