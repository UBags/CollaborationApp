pluginManagement {
    repositories {
        // These are the standard repositories for Gradle plugins.
        // By removing the content filter, we ensure Gradle can find KSP.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // These are the repositories for your app's libraries (dependencies).
        // This block is already correct.
        google()
        mavenCentral()
    }
}

rootProject.name = "Cortexa"
include(":app")