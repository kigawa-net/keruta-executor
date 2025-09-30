pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.jvm" || requested.id.id == "org.jetbrains.kotlin.plugin.spring") {
                useVersion("2.1.10")
            }
        }
    }
}

rootProject.name = "keruta-executor"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
