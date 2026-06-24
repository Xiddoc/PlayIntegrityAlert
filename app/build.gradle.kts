import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    jacoco
}

android {
    namespace = "com.xiddoc.playintegrityalert"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xiddoc.playintegrityalert"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Xposed API is provided by the framework at runtime — never bundled.
    compileOnly(libs.xposed.api)

    // JVM unit tests (Robolectric — no device/emulator needed).
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    // Xposed stub classes must be present on the unit-test runtime classpath so
    // hook-adjacent classes load; MockK replaces the stub method bodies in tests.
    testImplementation(libs.xposed.api)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Robolectric loads classes through its own classloader, so the JaCoCo agent must
// be told to count classes that report no code-source location.
tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

/*
 * Coverage scope: the unit suite targets 100% of the decision logic. The classes
 * below are thin framework glue with no decision logic of their own — the Xposed
 * entry/hook wiring, the XSharedPreferences binding, and the UI Activities — and
 * are verified by the build, lint, and the (non-blocking) e2e instead.
 */
val coverageExclusions = listOf(
    // Generated.
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    // Xposed-runtime glue.
    "**/XposedEntry*", "**/IntegrityServiceHook*", "**/XSharedConfigSource*",
    // UI Activities.
    "**/MainActivity*", "**/AppPickerActivity*",
)

private fun coveredClassTree() =
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude(coverageExclusions)
    }

private val unitTestExec =
    layout.buildDirectory.file("jacoco/testDebugUnitTest.exec")

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "Generates a JaCoCo coverage report for the debug unit tests."
    dependsOn("testDebugUnitTest")

    classDirectories.setFrom(coveredClassTree())
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(unitTestExec)

    reports {
        html.required.set(true)
        xml.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Fails the build unless the covered classes hit 100% line and branch coverage."
    dependsOn("testDebugUnitTest")

    classDirectories.setFrom(coveredClassTree())
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(unitTestExec)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
