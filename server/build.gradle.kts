plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":transport"))
            implementation(libs.kotlinxCoroutines)
            implementation(libs.kotlinxDatetime)
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinxCoroutinesTest)
                implementation(libs.kotlinxSerializationCore)
                implementation(libs.obor)
            }
        }
    }
}
