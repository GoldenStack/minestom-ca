plugins {
    id("java")
}

group = "net.minestom.ca"
version = "1.0"

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(rootProject)
    implementation(files("artefacts/Terra.jar"))
}

tasks.test {
}