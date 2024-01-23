* [x] Sometimes no answer, because done-status, but not `completed`
* [x] Strange issue with stored thread objects not being updated with title nor shared files
* [x] Confusing naming of stored-threads, which are not stored threads, but rather stored thread meta data
* [x] Render thread (session)
* [x] Restore last chat session on start
* [x] Session switcher UI (sessions palette)
* [x] Explore how to control the costs
  * [x] We're using a lot of tokens reaching the rate limit TPD of 500K
  * [x] Prioritize what to send with the context
  * [x] Prioritize what instructions we give
* [ ] Stop sending around thread everywhere we only need the thread-id
* [ ] Update the assistant with latest instructions
  * [ ] Also update the assistant with latest functions/tools defintions


## Function calling
* [x] current-file-path, because assumptions...
* [x] Better prompting, guiding the AI how to use the functions
  * [ ] Make it ask for current file, when it hasn't seen it before
* [x] We need to capture the context at the time of the query

## Joyride TODO

* [ ] Implement `abs`
* [ ] Upgrade SCI