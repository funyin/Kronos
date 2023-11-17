plugins {
    id("org.jetbrains.kotlin.jvm") version "1.8.20"
    kotlin("plugin.serialization") version "1.9.0"
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    mavenLocal()
}

dependencies {
//    implementation("com.funyinkash:Kronos:1.0-SNAPSHOT")
    implementation(project(":kronos"))
//    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.mongodb:bson-kotlinx:4.11.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    // Define the main class for the application.
    mainClass.set("example.AppKt")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}
