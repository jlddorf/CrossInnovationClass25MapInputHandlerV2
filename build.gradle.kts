plugins {
    kotlin("jvm") version "2.1.20"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.pi4j:pi4j-core:3.0.2")
    implementation("com.pi4j:pi4j-plugin-gpiod:3.0.2")
    implementation("com.pi4j:pi4j-plugin-raspberrypi:3.0.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}