rootProject.name = "trenuri"

pluginManagement {
    repositories { google(); gradlePluginPortal(); mavenCentral() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}

include(":infofer-client")
include(":app")
