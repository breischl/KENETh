plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    id("buildsrc.convention.kotlin-publishing")
    alias(libs.plugins.kotlinPluginSerialization)
}

kotlin {
    // Applying the default hierarchy explicitly allows us to extend it with custom source
    // sets below without triggering the "Not Applied Correctly" warning.
    applyDefaultHierarchyTemplate()

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

        // Shared source set for JS and Native — both lack @JvmInline.
        val nonJvmMain by creating {
            dependsOn(commonMain.get())
        }
        jsMain {
            dependsOn(nonJvmMain)
        }
        val nativeMain by getting {
            dependsOn(nonJvmMain)
        }
    }
}
