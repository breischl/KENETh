// Convention plugin for Maven Central publishing. Configures maven-publish, signing, and POM metadata.
// NMCP aggregation is configured separately in the root build.gradle.kts.
package buildsrc.convention

plugins {
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
}

// Configure POM metadata for all publications (KMP creates several per module)
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(project.name)
            description.set("KENETh - Kotlin EnergyNet Protocol library")
            url.set("https://github.com/breischl/keneth")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("breischl")
                    name.set("breischl")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/breischl/keneth.git")
                developerConnection.set("scm:git:ssh://github.com/breischl/keneth.git")
                url.set("https://github.com/breischl/keneth")
            }
        }
    }
}

// Sign all publications. Skipped locally if env vars are absent.
val signingKey = providers.environmentVariable("GPG_SIGNING_KEY").orNull
val signingPassword = providers.environmentVariable("GPG_SIGNING_PASSWORD").orNull
if (signingKey != null) {
    signing {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}
