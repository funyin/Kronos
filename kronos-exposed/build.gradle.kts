plugins {
    `java-library`
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.dokka")
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
    implementation("com.funyinkash:kachecontroller-exposed-jvm:1.0.6")
    implementation("com.funyinkash:kachecontroller-core:1.0.6")
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.44.1")
    implementation("org.jetbrains.exposed:exposed-json:0.44.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
    runtimeOnly("org.xerial:sqlite-jdbc:3.45.1.0")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    dependsOn(tasks.dokkaHtml)
    from("$buildDir/dokka/html")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            artifactId = "kronos-exposed"
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
    val file = File("${project.properties["signing.secretKeyFile"]}")
    useInMemoryPgpKeys(
        file.readText(),
        project.properties["signing.password"].toString(),
    )
    sign(publishing.publications["mavenJava"])
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}
