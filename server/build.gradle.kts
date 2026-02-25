plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":transport"))
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxDatetime)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.kotlinxSerializationCore)
    testImplementation(libs.obor)
}
