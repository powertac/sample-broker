Sample broker that handles all message types and operates in both wholesale and retail markets. It issues a set of tariffs as soon as possible, one for each PowerType detected in the customer records given in the bootstrap dataset. It then trades in the wholesale market using essentially the same strategy as the default broker. 

The current version assumes the server is running on localhost, and is not picky about passwords. You can change the server URL by editing the broker.properties file, or by using your own properties file.

You can run the broker from the command line using maven, as

`mvn exec:exec [-Dexec.args="<arguments>"]`

where arguments can include:

* `--config config-file.properties` specifies an optional properties file that can set username, password, server URL, and other broker properties. If not given, the file broker.properties in the current working directory will be used. 
* `--jms-url tcp://host.name:61616` overrides the JMS URL for the sim server. In a tournament setting this value is supplied by the tournament infrastructure, but this option can be handy for local testing.
* `--repeat-count n` instructs the broker to run n sessions, completely reloading its context and restarting after each session completes. Default value is 1.
* `--repeat-hours h` instructs the broker to attempt to run sessions repeatedly for h hours. This is especially useful in a tournament situation, where the number of games may not be known, but the duration of the tournament can be approximated. If repeat-count is given, this argument will be ignored.

If there are no non-default arguments, it is enough to simply run the broker as `mvn exec:exec`.
