## 0.0.8 - Jun 23
- **Publication restructure**: `kronos-mongo` and `kronos-exposed` now expose core types transitively via `api`/`compile` scope
- **Breaking**: Users no longer need to add `kronos` separately — the backend artifact pulls it in automatically
- **New**: `kronos-exposed` now published as a standalone artifact
- All artifacts are signed and include `-sources.jar` and `-javadoc.jar`

## 0.0.5 - May 11
- Implemented fix that allows jobs to be scheduled and executed within execution window
- Eagerly execute jobs instead of starting out with 1-minute delay
- **Breaking Changes** 
  - Updated [retryCount] param for onRetryFailed callback to be 1 index based instead of 0 index based  
  - Added [Job.onLastCycleDrop] as a call back to be notified when a jobs last cycle is dropped
- **Fixes**
  - Fixed issue of [Job.onDrop] being called multiple time 

## 0.0.4 - Apr 3

- Fixed bug that allowed jobs to run past maxCycles

- ## 0.0.3 - Dec 3
- Changed initialization to only collect connection string
- Updated Docs

## 0.0.2 - Dec 2
- Initial release