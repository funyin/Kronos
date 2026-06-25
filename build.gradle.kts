plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
    signing
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
    id("com.gradleup.nmcp") version "0.0.9"
}

group = "com.funyinkash"
version = "0.0.8"

allprojects {
    group = rootProject.group
    version = rootProject.version as String
}

repositories {
    mavenCentral()
    mavenLocal()
}



kotlin {

//    withSourcesJar(publish = false)
    jvm {
        jvmToolchain(19)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
                setJvmArgs(listOf("-XX:-OmitStackTraceInFastThrow", "-Xmx2g"))
            }
        }
    }
//    js {
//        browser {
//            commonWebpackConfig {
//                cssSupport {
//                    enabled.set(true)
//                }
//            }
//        }
//    }
//    val hostOs = System.getProperty("os.name")
//    val isArm64 = System.getProperty("os.arch") == "aarch64"
//    val isMingwX64 = hostOs.startsWith("Windows")
//    val nativeTarget = when {
//        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
//        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
//        hostOs == "Linux" && isArm64 -> linuxArm64("native")
//        hostOs == "Linux" && !isArm64 -> linuxX64("native")
//        isMingwX64 -> mingwX64("native")
//        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
//    }


    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("co.touchlab:kermit:2.0.4")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("com.funyinkash:kachecontroller-core:1.0.6")
                implementation("co.touchlab:kermit:2.0.4")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(project(":kronos-mongo"))
                implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

                implementation(platform("org.testcontainers:testcontainers-bom:1.19.3"))
                implementation("org.testcontainers:mongodb")
                implementation("com.redis:testcontainers-redis:1.7.0")

                implementation(kotlin("test"))
                implementation("io.mockk:mockk:1.13.8")
            }
        }
//        val jsMain by getting
//        val jsTest by getting
//        val nativeMain by getting
//        val nativeTest by getting
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    val displayName = "jvmMain"
    if (!dokkaSourceSets.names.contains(displayName)) {
        dokkaSourceSets.create(displayName)
    }
    dokkaSourceSets.named(displayName) {
        sourceRoots.from(project.layout.projectDirectory.dir("src/jvmMain/kotlin"))
    }
}

val javaDocJar by tasks.registering(Jar::class) {
    dependsOn(tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from("$buildDir/dokka/html")
}

publishing {
    publications {
        withType<MavenPublication> {
            val target = this.name

            if (target == "kotlinMultiplatform") {
                artifactId = "kronos"
            } else {
                artifactId = "kronos-$target"
                artifact(javaDocJar)
            }

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
        // AUTOMATIC releases to Central immediately after validation.
        // Change to "USER_MANAGED" to review in the portal UI before releasing.
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
        publishing.publications.forEach {
            sign(it)
        }
    }
}

koverReport {
    verify {
        rule("Min Coverage") {
            bound {
                minValue = 85
            }
        }
    }
}

