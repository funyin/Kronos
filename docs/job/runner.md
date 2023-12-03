---
comments: true
---

The Runner is responsible for managing the validation and execution of jobs after they have been scheduled.

There are a few edge cases that you can cater for by directing the runner on what to do in those cases

1. What happens when the runner comes across a job that was scheduled to be executed in the past but did not get
   executed e.g The server
   was down at the time
2. What happens when multiple instances of kronos(microservices) are trying to run jobs. Although this is not
   recommended, you should delegate this a single service

## 1. OverShot Action

How you want Kronos to handle a Job when it is over due is termed `OvershotAction`.

There are three Options:

- **Fire** :  The Job is Run immediately without any validation
- **Drop** :  The Job is dropped immediately
- **Nothing** : No things happens. This is not recommended because it can bloat your db if not handled properly

## 2. Locks

This is just a failsafe to prevent multiple instances of kronos(in the case of microservices) from running the same job.
Once execution on a job starts, the job is locked and prevents other instances from running it