Sample broker that handles all message types and operates in both wholesale and retail markets. It issues a set of tariffs as soon as possible, one for each PowerType detected in the customer records given in the bootstrap dataset. It then trades in the wholesale market using essentially the same strategy as the default broker. 

The current version assumes the server is running on localhost, and is not picky about passwords. You can change the server URL by editing the broker.properties file, or by using your own properties file.

You can run the broker from the command line using maven, as

`mvn exec:exec [-Dexec.args="--config config-file.properties"]`

where config-file.properties is an optional properties file that can set username, password, server URL, and other broker properties. If not given, the file broker.properties in the current working directory will be used. In this case, it is enough to simply run the broker as `mvn exec:exec`.
