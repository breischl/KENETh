plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinxDatetime)
            implementation(libs.obor)
            implementation(libs.kotlinxSerializationCore)
            implementation(libs.kotlinxIo)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotestProperty)
            implementation(libs.kotlinxCoroutinesTest)
        }
    }
}
