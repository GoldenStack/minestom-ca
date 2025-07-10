plugins {
    `java-library`
}

dependencies {
    api("net.minestom:minestom:2025.07.10b-1.21.7")

    // https://mvnrepository.com/artifact/it.unimi.dsi/fastutil
    implementation("it.unimi.dsi:fastutil:8.5.16")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.13.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.13.3")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
