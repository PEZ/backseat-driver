* [x] Sometimes no answer, because done-status, but not `completed`
* [ ] Strange issue with stored thread objects not being updated with title nor shared files
* [ ] Confusing naming of stored-threads, which are not stored threads, but rather stored thread meta data
* [x] Render thread (session)
* [x] Restore last chat session on start
* [ ] Session switcher UI (sessions palette)
* [ ] Explore how to control the costs
  * [ ] We're using a lot of tokens reaching the rate limit TPD of 500K
  * [ ] Prioritize what to send with the context
  * [ ] Prioritize what instructions we give
* [ ] Stop sending around thread everywhere we only need the thread-id
* [ ] Update the assistant with latest instructions
  * [ ] Also update the assistant with latest functions/tools defintions


## Function calling
* [x] current-file-path, because assumptions...
* [ ] We need much better prompting, guiding the AI how to use the functions
  * [ ] Make it ask for current file, when it hasn't seen it before
  

## Joyride TODO

* [ ] Implement `abs`
* [ ] Upgrade SCI