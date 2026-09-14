pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when {
                requested.id.id == "com.android.application" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                requested.id.id.startsWith("org.jetbrains.kotlin") ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Hexora"
include(":app")
include(":core:filesystem")
