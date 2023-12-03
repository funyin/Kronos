---
comments: true
---

## Starting Kronos

Kronos should be initialized once through the lifecycle of your application.

```kotlin
val mongoClient: MongoClient = MongoClient.create("mongodb://localhost:27017")
val redisClient: RedisClient = RedisClient.create("redis://localhost:6379")
val connection: StatefulRedisConnection<String, String> = redisClient.connect()

Kronos.init(mongoClient = mongoClient, redisConnection = connection)
```
Throws IllegalState Exception on attempt to initialize a second time

## Job
Jobs are categories of tasks that are use to define execution.

### Define Job
```kotlin
object SayHello : Job {
    override val name: String
        get() = "say-hello"

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        super.execute(cycleNumber, params)
        println("Hello ${params["firsName"]} ${params["lastName"]} $cycleNumber")
        return true
    }
}
```

### Register Job
```kotlin
Kronos.register(SayHello)
```

## Schedule Job

This Job will start in 1 minute and run once

```kotlin
Kronos.schedule(
        SayHello.name,
        startTime = Instant.now().plusSeconds(60).toEpochMilli(),
        params = mapOf(
            "firsName" to "Funyin",
            "lastName" to "Kash"
        ),
    )
```