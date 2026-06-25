# Kronos

A persistent, distributed job scheduler for Kotlin JVM — without the
complexity of cron expressions.

Jobs run with minute-level precision, survive service restarts, and
coordinate safely across multiple instances via a conditional atomic lock.

## At a glance

=== "MongoDB + Redis"

    ```kotlin
    import kronos.mongo.init

    Kronos.init(
        mongoConnectionString = "mongodb://localhost:27017",
        redisConnectionString = "redis://localhost:6379"
    )

    Kronos.register(SendReport)

    Kronos.schedule(jobName = SendReport.name, params = mapOf("id" to "123"))

    Kronos.schedulePeriodic(
        jobName = SendReport.name,
        periodic = Periodic.everyDay(hour = 9, minute = 0),
        params = mapOf("id" to "123")
    )
    ```

=== "PostgreSQL + Redis"

    ```kotlin
    import kronos.exposed.init

    Kronos.init(
        dataSource = ds,
        cache = RedisCacheClient("redis://localhost:6379")
    )
    ```

=== "Sqlite + In-Memory Cache"

    ```kotlin
    import kronos.exposed.init
    import com.funyinkash.kachecontroller.cache.InMemoryCacheClient
    import org.sqlite.SQLiteDataSource

    val ds = SQLiteDataSource().apply {
        url = "jdbc:sqlite:kronos.db"
    }

    Kronos.init(
        dataSource = ds,
        cache = InMemoryCacheClient()
    )
    ```

=== "PostgreSQL + In-Memory Cache"

    ```kotlin
    import kronos.exposed.init
    import com.funyinkash.kachecontroller.cache.InMemoryCacheClient

    val ds = HikariDataSource().apply { /* ... */ }

    Kronos.init(
        dataSource = ds,
        cache = InMemoryCacheClient()
    )
    ```

See [Quick Start](quick_start.md) to get running in minutes.
