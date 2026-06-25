plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
    signing
    id("com.gradleup.nmcp") version "0.0.9"
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    api(project(":"))
    implementation("co.touchlab:kermit:2.0.4")
    implementation("com.funyinkash:kachecontroller-mongo:1.0.6")
//    implementation("com.funyinkash:kachecontroller-core:1.0.6")
    implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
    implementation("org.mongodb:bson-kotlinx:4.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    val displayName = "main"
    if (!dokkaSourceSets.names.contains(displayName)) {
        dokkaSourceSets.create(displayName)
    }
    dokkaSourceSets.named(displayName) {
        sourceRoots.from(project.layout.projectDirectory.dir("src/main/kotlin"))
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
    val displayName = "main"
    if (!dokkaSourceSets.names.contains(displayName)) {
        dokkaSourceSets.create(displayName)
    }
    dokkaSourceSets.named(displayName) {
        sourceRoots.from(project.layout.projectDirectory.dir("src/main/kotlin"))
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn(tasks.dokkaHtml)
    from("$buildDir/dokka/html")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = "kronos-mongo"
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)
            pom {
                name.set("Kronos")
                description.set("Kronos is a persistent Job scheduling library.")
                url.set("https://funyin.github.io/Kronos/")
                issueManagement {
                    system.set("Github")
                    url.set("https://github.com/funyin/Kronos/issues")
                }
                packaging = "jar"
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("funyin")
                        name.set("Funyinoluwa Kashimawo")
                        email.set("funyin.kash@gmail.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/funyin/Kronos.git")
                    developerConnection.set("scm:git:ssh://github.com/funyin/Kronos.git")
                    url.set("https://github.com/funyin/Kronos")
                }
            }
        }
    }
}

nmcp {
    publishAllPublications {
        username.set(project.properties["centralPortal.username"].toString())
        password.set(project.properties["centralPortal.password"].toString())
        publicationType.set("AUTOMATIC")
    }
}

signing {
    val keyFile = File("${project.properties["signing.secretKeyFile"]}")
    if (keyFile.exists()) {
        useInMemoryPgpKeys(
            keyFile.readText(),
            project.properties["signing.password"].toString(),
        )
        sign(publishing.publications["mavenJava"])
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}
