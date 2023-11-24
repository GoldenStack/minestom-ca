plugins {
    `java-library`
    `maven-publish`
}

group = "dev.goldenstack.minestom_ca"
version = "1.0"
description = "Implementing vanilla Minecraft with cellular automata"

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    val minestom = "dev.hollowcube:minestom-ce:e146a9393e"

    implementation(minestom)

    // https://mvnrepository.com/artifact/org.jocl/jocl
    implementation("org.jocl:jocl:2.0.5")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation(minestom)
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

configure<JavaPluginExtension> {
    withSourcesJar()
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}