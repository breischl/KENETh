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
        suppressWarnings = true
    }
}

kotlin {
    jvmToolchain(25)

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
