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
    implementation("com.pi4j:pi4j-plugin-linuxfs:3.0.2")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-reload4j:2.0.17")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
tasks {
    task("run", JavaExec::class) {
        group = "run"
        description = "Starts the application, listens to input signals and relays them via sockets"
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = "org.example.MainKt"
    }
}
kotlin {
    jvmToolchain(21)
}