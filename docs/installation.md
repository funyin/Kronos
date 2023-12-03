---
comments: true
---

# Installation

Kronos is pushed to MavenCentral repository as kotlin multiplatform library.

=== "Kotlin Gradle Script" 
    
    Add Maven Central repository:

    ```kotlin
    repositories {
        mavenCentral()
    }
    ```

    Add dependencies:
    
    ```kotlin
    implementation("com.funyinkash:kronos:0.0.2")
    ```

=== "Gradle"
    
    Add Maven Central repository:
        
    ```groovy
    repositories {
        mavenCentral()
    }
    ```

    Add dependencies (you can also add other modules that you need):
    
    ```groovy
    implementation 'com.funyinkash:kronos:0.0.2'
    ```

=== "Maven" 

    Add Maven Central repository to section:

    ```xml
    
    <repositories>
        <repository>
            <id>central</id>
            <url>https://repo1.maven.org/maven2</url>
        </repository>
    </repositories>
    ```
    
    Add dependency:
    
    ```xml
    
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kronos</artifactId>
        <version>0.0.2</version>
    </dependency>
    ```

## Targets
Specific dependencies. The Multiplatform dependency above should be sufficient, but these are also published 

=== "jvm"
    ```kotlin
    implementation("com.funyinkash:kronos-jvm:0.0.2")
    //supporting libraries that are required to initialize kronos
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    ```
    
=== "js"
    
    Comming soon

=== "android"
    
    Comming soon

=== "linux"
    
    Comming soon

=== "ios"
    
    Comming soon