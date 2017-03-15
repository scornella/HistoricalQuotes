## HistoricalQuotes
Author: Steve Cornella

Demo app to display basic skills required to build a modern REST application.

Running:

  * do an `sbt run`
  * will start up in development mode with hot-reload
  * database is in-memory and will rebuild on each reload

Example Queries:

  * get yahoo stock quotes for 2011: 
  `curl -H "Content-Type: application/json" -X POST -d '{"ticker":"YHOO"}' http://localhost:9000/get-quotes`
  * get apple quotes for 2001 sorted by close price:
  `curl -H "Content-Type: application/json" -X POST -d '{"ticker":"AAPL", "sort": "close.desc"}' http://localhost:9000/get-quotes`
  
Available Options:

  * "ticker": "YHOO" - stock ticker to query for
  * "sort": "close.desc" - field and direction to sort output
  * "limit": 10 - number of records to return
  * "skip": 2 - number of records to offset/skip from beginning of output
 
Other Thoughts:

  * Decided to learn Play for this exercise which was a nice learning experience but it definitely lengthened the dev process
  * Tried to limit the amount of time spent so I cut corners with tests and some nice-to-haves like cleaner handling of configuration
  * Play documentation masquerades as being complete but it seems that some significant changes between 2.4.x and 2.5.x broke a lot of things
  * Overall I enjoyed the experience, Play offers a pretty nice experience if you stay within the lines
 
