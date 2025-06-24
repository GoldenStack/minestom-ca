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
}

dependencies {
    api("com.github.Minestom:Minestom:b39badc77b")

    // https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    implementation("it.unimi.dsi:fastutil:8.5.15")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.2")
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