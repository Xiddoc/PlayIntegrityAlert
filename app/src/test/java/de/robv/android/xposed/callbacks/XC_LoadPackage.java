package de.robv.android.xposed.callbacks;

/** Functional unit-test fake of {@code XC_LoadPackage} with its public-field param. */
public final class XC_LoadPackage {

    private XC_LoadPackage() {}

    public static final class LoadPackageParam {
        public String packageName;
        public ClassLoader classLoader;

        public LoadPackageParam() {}
    }
}
