Power TAC Sample Broker
=======================

The sample broker is intended to help broker developers by providing both a foundation that interfaces correctly with the Power TAC infrastructure, and an example of a working broker agent. It handles all message types and operates in both wholesale and retail markets. It issues a set of tariffs as soon as possible, one for each PowerType detected in the customer records given in the bootstrap dataset. It then trades in the wholesale market using essentially the same strategy as the default broker. 

Without changing anything, the current version assumes the server is running on localhost, and is not picky about passwords. You can change the server URL by editing the broker.properties file, or by using your own properties file. Passwords are generally ignored outside a tournament environment.

Import into IDE
---------------

Most developers will presumably want to work with the code using an IDE such as [STS](http://www.springsource.org/sts). The sample-broker package is a maven project, so it works to just do File->Import->Existing Maven Projects and select the sample-broker directory (the directory containing the pom.xml file). You may wish to change the "name" attribute in the pom.xml to match the name of your broker. You can set up a simple "run configuration" to allow you to run it from the IDE. It is an AspectJ/Java app, the main class is `org.powertac.samplebroker.core.BrokerMain`, and there are no arguments required unless you wish to specify an alternate config file or pass other options (see below).

Run from command line
---------------------

This is a maven project. You can run the broker from the command line using maven, as

`mvn compile exec:exec [-Dexec.args="<arguments>"]`

where arguments can include:

* `--config config-file.properties` specifies an optional properties file that can set username, password, server URL, and other broker properties. If not given, the file broker.properties in the current working directory will be used. 
* `--jms-url tcp://host.name:61616` overrides the JMS URL for the sim server. In a tournament setting this value is supplied by the tournament infrastructure, but this option can be handy for local testing.
* `--repeat-count n` instructs the broker to run n sessions, completely reloading its context and restarting after each session completes. Default value is 1.
* `--repeat-hours h` instructs the broker to attempt to run sessions repeatedly for h hours. This is especially useful in a tournament situation, where the number of games may not be known, but the duration of the tournament can be approximated. If repeat-count is given, this argument will be ignored.
* `--no-ntp` if given, tells the broker to not rely on system clock synchronization, but rather to estimate the clock offset between server and broker. Note that this will be an approximation, but should at least get the broker into the correct timeslot.
* `--queue-name name` tells the broker to listen on the named queue for messages from the server. This is really only useful for testing, since the queue name defaults to the broker name, and in a tournament situation is provided by the tournament manager upon successful login.
* `--server-queue name` tells the broker the name of the JMS input queue for the server. This is also needed only for testing, because the queue name defaults to 'serverInput' and in a tournament situation is provided by the tournament manager upon successful login.

If there are no non-default arguments, and if the broker has already been compiled, then it is enough to simply run the broker as `mvn exec:exec`.

Prepare an executable jar
---------------------------

Power TAC and other competitive simulations are research tools. A major advantage of the competitive simulation model is the ability to test ideas in a competitive environment. This requires competitors, which means we need to share our broker implementations with each other. Since most teams will be understandably reluctant to share source code, we need a method to share binaries. This package comes with an ability to create an "executable jar" file from source that includes all dependencies, and typically needs only a configuration file to work. You can create an executable jar as

`mvn clean package`

which will produce a file `target/name.jar`, where `name` is the "name" element near the top of the pom.xml. All classpath resources will be included (files in `src/main/resources`) in addition to the compiled classes and all dependencies. To share your broker, you need to bundle the executable jar with any configuration files needed by your implementation that are not on the classpath (such as broker.properties), and of course a README file that tells others how to use it.

You can then run your broker agent as

`java -jar name.jar [args]`

