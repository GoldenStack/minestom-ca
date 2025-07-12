plugins {
    id("java")
}

group = "net.minestom.ca"
version = "1.0"

java {
    withJavadocJar()
    withSourcesJar()
}

dependencies {
    implementation(project(":engine"))
    implementation(files("artefacts/Terra.jar"))

    implementation("org.everbuild.blocksandstuff:blocksandstuff-blocks:1.4.0-SNAPSHOT")
}

tasks.test {
}