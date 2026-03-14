plugins {
    kotlin("multiplatform")
}

kotlin {
    js(IR) {
        browser {
            binaries.executable()
        }
    }

    sourceSets {
        jsMain.dependencies {
            // Explicit dependencies on :core and :transport are required so that we can see & use their classes
            // We do not get visibility to them transitively from the :server dependency
            implementation(project(":core"))
            implementation(project(":transport"))
            implementation(project(":server"))
            implementation(libs.kotlinxCoroutines)
        }
        jsTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
