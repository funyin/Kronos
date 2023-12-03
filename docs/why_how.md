---
title: Why, How
---

### The Philosophy of Kronos

Kronos was developed out of the need for a simple framework agnostic job scheduling tool in kotlin.
There were some options but they didn't fit my use case because most of them were written in java or for spring.

Here are some examples and why they didn't work for my use-case

- [Quartz](https://github.com/quartz-scheduler/quartz/tree/main) :
    - Uses Java threads instead of coroutines witch is fine but didn't match my preference.
    - Uses a relational database for persistence, I wanted to use mongodb
    - Didn't have a caching layer over the db
- [Timer](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.concurrent/java.util.-timer/schedule.html#schedule): The
  timer from the kotlin standard library
    - Was not persistent
    - Job status cannot be checked after scheduling
- [Cron4J](https://www.sauronsoftware.it/projects/cron4j/): A scheduler for the Java platform which is very similar to
  the UNIX cron daemon
    - Java
    - I am not very familiar with cron jobs
    - Not Observable
- [Spring Schedule](https://spring.io/guides/gs/scheduling-tasks/)
    - Dependent on the spring framework
- [KJob](https://spring.io/guides/gs/scheduling-tasks/): A lightweight coroutine based persistent job/cron scheduler written in Kotlin
    - Seemed like the perfect fit, Kotlin, Coroutines and Mongodb but I encountered an [error](https://github.com/justwrote/kjob/issues/8#issue-1997120456) that I could not resolve
    - The last commit was 3 years ago

After my last try with kJob I decided to attempt creating mine and took some inspiration from it.

Documentation format inspired by Quartz, KGraphql and powered by Material for MkDocs

## How
Kronos is a really light weight library with only a few files. The hard part was figuring out the expected execution for a job after the
server has been restarted and that's when the Job Registration pattern of KJob made sense.

Next was the frequency of checks, a regular heartbeat is required to run schedules.

Two options:

- Delay the coroutine until the start time of the Job for each Job. Less strain on the system but the time control becomes quite coarse and hard to manage
- Delay the coroutine by the smallest measure that the system can manage and that jobs will not be lost between ticks. e.g If the job is set to start in 3 minutes and the 
tick interval is 2 minutes or 5 minutes, some jobs will fall through. I cam to the conclusion that the smallest possible tick is 1 minute to achieve fine time control. 
This created two constraints
    - The constraint of the smallest possible interval to be 1 minute and the smallest possible time unit to be minutes i.e you won't be able to schedule something for a second. 
        - This I can live with. (Please make use of the Kotlin timer API if you are constrained by this)
    - The system will hit the db every minute, which will definitely cause a spike in cost
        - I catered for this by adding a caching layer over the db calls using [KacheController](https://github.com/funyin/KacheController)

The rest is magic, just added some utility callbacks and functions for observing and managing a job