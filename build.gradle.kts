plugins {
    kotlin("multiplatform") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jetbrains.dokka") version "1.9.10"
    `maven-publish`
    signing
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

group = "com.funyinkash"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
//    maven {
//        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
//    }
//    mavenLocal()
}

kotlin {
    jvm {
        jvmToolchain(19)
        withJava()
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
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
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("com.funyinkash.kachecontroller:mongo-redis:1.0.0")
                implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
                implementation("org.mongodb:bson-kotlinx:4.11.1")
                implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
            }
        }
        val jvmTest by getting {
            dependencies {
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

koverReport{
    verify{
        rule("Min Coverage"){
            bound {
                minValue = 85
            }
        }
    }
}



publishing {

    val javaDocJar = tasks.register<Jar>("javadocJar") {
        dependsOn(tasks.dokkaHtml)
        archiveClassifier.set("javadoc")
        from("$buildDir/dokka/html")
    }

//    val sourcesJar = tasks.register<Jar>("sourcesJar") {
//        dependsOn(tasks.classes)
//        archiveClassifier.set("sources")
//        from(sourceSets.main)
//    }


    publications {
        create<MavenPublication>("maven") {
//                artifactId = "mongo-redis"

            from(components["kotlin"])
            artifact(javaDocJar)

            pom {
                name.set("Kronos")
                description.set("Kronos is a persistent Job scheduling library.")
                url.set("https://funyin.github.io/KacheController/mongo-redis/index.html")
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

    repositories {
        maven {
            name = "OSSHR"

            val isSnapshot = version.toString().endsWith("SNAPSHOT")
            url = uri(
                if (isSnapshot)
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                else
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
            )
            credentials {
                username = project.properties["osshr.username"].toString()
                password = project.properties["osshr.password"].toString()
            }
        }
    }
}

signing {
    val file = File("${projectDir}/${project.properties["signing.secretKeyFile"]}")
    useInMemoryPgpKeys(
        file.readText(),
        project.properties["signing.password"].toString(),
    )
    sign(publishing.publications["maven"])
}
