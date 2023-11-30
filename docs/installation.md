# Installation

Kronos is pushed to MavenCentral repository as kotlin multiplatform library.

### Supported targets
- jvm
- <s>native</s>
- <s>js</s>

=== "Kotlin Gradle Script" 
    
    Add Maven Central repository:

    ```kotlin
    repositories {
        mavenCentral()
    }
    ```

    Add dependencies:
    
    ```kotlin
    implementation("com.funyinkash:kronos:1.0.0")
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
    implementation 'com.funyinkash:kronos:1.0.0'
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
        <version>1.0.0</version>
    </dependency>
    ```

## Platforms

=== "jvm"
    
    Kronos currently uses [mongodb](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/) as the database and [redis](https://lettuce.io/) as the cache.
    So you'll need to install dependencies for those
    
    ```kotlin
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