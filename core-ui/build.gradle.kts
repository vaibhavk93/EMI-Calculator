plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Presentation logic with no Android dependency, so number formatting, input parsing
// and screen state can be unit-tested without a device or an emulator. The Compose
// layer in :app is a thin rendering shell over this.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":core-calc"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
