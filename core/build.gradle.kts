plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.kotlinxDatetime)
    implementation(libs.obor)
    implementation(libs.kotlinxSerializationCore)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotestProperty)
}
