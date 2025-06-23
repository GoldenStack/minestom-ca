rootProject.name = "minestom-ca"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.7.0")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // For placement rules
        maven {
            name = "asordaPublic"
            url = uri("https://mvn.everbuild.org/public")
        }
    }
}

include("demo")