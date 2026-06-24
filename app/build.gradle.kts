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
        minSdk = 30
        targetSdk = 35
        // Version is injected by the release workflow (autobumped from the latest
        // git tag); the literals below are the local-build fallback.
        versionCode = (project.findProperty("VERSION_CODE") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("VERSION_NAME") as String?) ?: "1.0"
    }

    signingConfigs {
        // Release signing is supplied by CI via environment variables (the keystore
        // itself is decoded from a secret). When they're absent — local builds, or CI
        // without the signing secrets — the release build falls back to the debug key
        // (see buildTypes.release) so it still produces an installable APK.
        create("release") {
            val storeFilePath = System.getenv("PIA_KEYSTORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("PIA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PIA_KEY_ALIAS")
                keyPassword = System.getenv("PIA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 full-mode: shrink + obfuscate code and strip unused resources. The
            // Xposed entry points and the reflectively-overwritten isModuleActivated()
            // are preserved by proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (System.getenv("PIA_KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
    // The Xposed API is compileOnly in main. For unit tests we provide our own
    // functional fakes of the few Xposed types under src/test/java/de/robv/... (and
    // android.app.AndroidAppHelper), so the real hook code runs on the JVM. The
    // published api jar is a throwing stub, so it is deliberately NOT on the test
    // classpath.
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
 * Coverage scope: the unit suite targets 100% line and branch of every hand-written
 * class — including the Xposed hook wiring and the UI Activities, which run on the
 * JVM via the functional Xposed fakes under src/test. Only generated code is excluded.
 */
val coverageExclusions = listOf(
    "**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*",
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
