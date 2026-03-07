// Root project build file. Aggregates all module publications for Maven Central.
plugins {
    // NMCP aggregation plugin is on the classpath via buildSrc; no version needed here.
    id("com.gradleup.nmcp.aggregation")
}

nmcpAggregation {
    centralPortal {
        username = providers.environmentVariable("MAVEN_CENTRAL_USERNAME")
        password = providers.environmentVariable("MAVEN_CENTRAL_PASSWORD")
        publishingType = "AUTOMATIC"
    }
}

dependencies {
    nmcpAggregation(project(":core"))
    nmcpAggregation(project(":transport"))
    nmcpAggregation(project(":server"))
}
