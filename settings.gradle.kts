pluginManagement {
    resolutionStrategy { eachPlugin {
        if (requested.id.id == "org.jetbrains.kotlin.kapt") useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
    } }
    repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "NetBuddy"
include(":app")
