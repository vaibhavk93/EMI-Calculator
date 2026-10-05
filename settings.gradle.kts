rootProject.name = "emi-calculator"

// NOTE: `google()` is intentionally absent. This project's calculation core is
// pure Kotlin/JVM so it resolves entirely from Maven Central. The Android UI
// modules added later will need google(), which requires dl.google.com to be
// reachable (see PLAN.md section 5).
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core-calc")
