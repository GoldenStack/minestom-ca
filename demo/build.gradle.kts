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

    // For placement rules
    maven {
        name = "asordaPublic"
        url = uri("https://mvn.everbuild.org/public")
    }
}

dependencies {
    implementation(rootProject)
    implementation(files("artefacts/Terra.jar"))

    implementation("org.everbuild.blocksandstuff:blocksandstuff-blocks:1.2.0-SNAPSHOT")
}

tasks.test {
}