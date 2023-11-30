---
description: "Jobs" 
---

# Job

Jobs are responsible for running and managing tasks. After tasks are scheduled, they are associated with a **Job** by
the name and executed within the Job.

## Creating a Job

To Create a Job you simply Extend the **Job** Object

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

* The **cycleNumber** is the number of times the job has been executed from 1 -> n
* The job also provides the **params** that were provided when the job was scheduled. You can serialize and deserialize objects through this
* The execution should return `true` to indicate that the job was successful. 

### Retries
In the case that `false` is returned(i.e task failed) you have the opportunity to set a number of retries with

```kotlin
object SayHello : Job {
    ...

    override val retries: Int
        get() = 2
    ...
}
```

This will rerun the execution without any delay for the specified number of retries until the execution returns true.
This call back is also provided for each failure

```kotlin
object SayHello : Job {

    fun onRetryFail(retryCount: Int, cycleNumber: Int, params: Map<String, Any>) {
        
    }
}
```

