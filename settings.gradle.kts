rootProject.name = "emi-calculator"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":core-calc")
include(":core-ui")

// The :app module needs the Android SDK and the Android Gradle Plugin, which comes from
// Google's Maven repository. Both are absent in some CI and cloud environments, and the
// Android plugin fails at configuration time rather than gracefully — which would take
// the pure-Kotlin modules down with it.
//
// So :app is included only when an SDK is actually present. On a workstation or any
// machine with ANDROID_HOME set (or sdk.dir in local.properties) it is included as
// normal; elsewhere the calculation and presentation modules still build and test.
val androidSdkPresent: Boolean = run {
    val fromEnv = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    val fromProperties = file("local.properties")
        .takeIf { it.exists() }
        ?.let { java.util.Properties().apply { it.inputStream().use(::load) }.getProperty("sdk.dir") }
    val dir = fromEnv ?: fromProperties
    !dir.isNullOrBlank() && file(dir).isDirectory
}

if (androidSdkPresent) {
    include(":app")
} else {
    logger.lifecycle(
        "No Android SDK found, so :app is excluded from this build. " +
            "Set ANDROID_HOME or add sdk.dir to local.properties to build the app.",
    )
}
