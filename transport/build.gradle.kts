plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(libs.kotlinxCoroutines)
            implementation(libs.kotlinxSerializationCore)
            implementation(libs.obor)
            implementation(libs.kotlinxIo)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinxCoroutinesTest)
        }
    }
}
