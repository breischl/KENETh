// Convention plugin for Kotlin Multiplatform modules (JVM + JS targets).
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    id("dev.detekt")
}

version = "0.0.1-alpha"

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        // Suppress expect/actual classes Beta warning
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
        suppressWarnings = false
    }
}

kotlin {
    jvmToolchain(24)

    // Note: KGP internally adds JVM artifacts to the deprecated `archives` configuration,
    // causing a "Deprecated Gradle features" warning about Gradle 10 compatibility.
    // This is a KGP bug tracked at https://youtrack.jetbrains.com/issue/KT-78620.
    // No workaround is available in project code; upgrade KGP when a fix is released.
    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            testLogging { events(TestLogEvent.FAILED, TestLogEvent.PASSED, TestLogEvent.SKIPPED) }
        }
    }

    js(IR) {
        nodejs()
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
    }

    linuxArm64()
}
