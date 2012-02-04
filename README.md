Sample broker that handles all message types and operates in both wholesale and retail markets. It issues two tariffs as soon as possible, and trades in the wholesale market using essentially the same strategy as the default broker.

The current version assumes the server is running on localhost, and is not picky about passwords.

You can run the broker from the command line using maven, as

`mvn exec:exec -Dexec.args="username server-url"`

where `server-url` is an option argument needed if the server cannot be found at tcp://localhost:61616.
