plugins {
    `java-library`
    `maven-publish`
}

group = "net.goldenstack.minestom_ca"
version = "1.0"
description = "Implementing vanilla Minecraft with cellular automata"

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(23)
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    val minestom = "net.minestom:minestom-snapshots:1_21_5-c4814c2270"

    implementation(minestom)

    // https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    implementation("it.unimi.dsi:fastutil:8.5.15")

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