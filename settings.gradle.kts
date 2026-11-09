pluginManagement {
    repositories {
        // Maven Central first: no Google-hosted runtime artifacts are required by this app.
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "MyLinks"
include(":app")
