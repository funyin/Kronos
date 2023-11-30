# Roadmap
Here is an unordered list of features that I would like to see in kjob. If you consider one of them important please open an issue.

- InMemory Kronos
  - non-persistent to serve as a replacement for tests or console applications 
- Backoff algorithm for failed jobs
  - An algorithm to control the retry delay between failed executions
- Dashboard 
  - A standalone application that can provide a ui(kobweb) ,be hosted to display, log and manage jobs.\
  Authentication will be handled by time based randomly updating values in the db. \
  If you have access to those values(your db). Then you are authorized to access the UI