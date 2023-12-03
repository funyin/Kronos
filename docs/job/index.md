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

## Executing Job

### Success
Callback for when the job is successful i.e `execute` returns true
```kotlin
object SayHello : Job {

    fun onSuccess(cycleNumber: Int, params: Map<String, Any>) {
        println("KRONOJOB($name) Success: ")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

}
```

### Failure
Callback for when the job is fails i.e `execute` returns false.
This is only called for the first failure. 
If it fails again then `onRetryFail` is called
```kotlin
object SayHello : Job {

    fun onFail(cycleNumber: Int, params: Map<String, Any>) {
        
    }

}
```

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

## Challenge Execution
If the Job has passed the internal execution by kronos and is about to run, 
you can prevent it from running by adding your own validation. e.g
A Job that runs every day, but you don't want the Job to run if it is a public holiday.

The Job will not run if `challengeRun` returns true

```kotlin
object SayHello : Job {

    //This Job will not run if the cycle is an even number
    fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
        return cycleNumber % 2 == 0
    }
}
```

## Periodic Job Loaded
This is called when the next job for a periodic job is scheduled.
In case you are making use of the job Id when you scheduled the job, you can listen to this to update the Id 
you are referencing

```kotlin
object SayHello : Job {

    //This Job will not run if the cycle is an even number
    fun periodicJobLoaded(originJobId: String, nextJobId: String) {}
}
```


