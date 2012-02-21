/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.powertac.samplebroker;

//import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import joptsimple.*;

/**
 * This is the top level of the Power TAC server.
 * 
 * @author John Collins
 */
public class BrokerMain {
	// static private Logger log = Logger.getLogger(BrokerMain.class);

	/**
	 * Sets up the broker. Single command-line arg is the username
	 */
	public static void main(String[] args) {
		AbstractApplicationContext context = new ClassPathXmlApplicationContext(
				"broker.xml");
		context.registerShutdownHook();

		BrokerTournamentService brokerTournamentService = (BrokerTournamentService) context
				.getBeansOfType(BrokerTournamentService.class).values()
				.toArray()[0];
		

		// Get username from command-line.
		String username = "Sample";
		String jmsBrokerUrl = null;
		String tsUrl = null;
		String authToken = null;
		String gameToken = null;
		OptionParser parser = new OptionParser("u:j:t::");
		OptionSet options = parser.parse(args);

		/*
		 * if (args.length < 1) {
		 * System.out.println("Username not given - default is 'Sample'"); }
		 * else { username = args[0]; if (args.length == 2) { jmsBrokerUrl =
		 * args[1]; }else if(args.length == 3) {
		 * if(args[1].equalsIgnoreCase("-t")){ tsUrl = args[2]; // if a tsUrl is
		 * specified override the config, else use the config } }
		 * 
		 * }
		 */

		if (options.has("t") ^ (options.has("u") && options.has("j"))) {
			tsUrl = (String) options.valueOf("t");
			// If a tournament scheduler was specified try to connect to it
			if (tsUrl != null) {
				brokerTournamentService.init();
				jmsBrokerUrl = brokerTournamentService.login(tsUrl);
			} else {
				username = (String) options.valueOf("u");
				jmsBrokerUrl = (String) options.valueOf("j");
			}
		} else {
			System.out.println("Has -t option: " + options.has("t"));
			System.out
					.println("Usage: {-u username -j jmsUrl } | {-t tournamentSchedulerUrl }");
			System.exit(0);
		}

		// find the Broker and JmsManagementService beans, hook up the jms queue
		SampleBroker broker = (SampleBroker) context
				.getBeansOfType(SampleBroker.class).values().toArray()[0];
		broker.init(username);
		JmsManagementService jmsm = (JmsManagementService) context
				.getBeansOfType(JmsManagementService.class).values().toArray()[0];
		BrokerMessageReceiver receiver = (BrokerMessageReceiver) context
				.getBeansOfType(BrokerMessageReceiver.class).values().toArray()[0];
		String brokerQueueName = broker.getBroker().toQueueName();

		jmsm.init(jmsBrokerUrl);
		jmsm.registerMessageListener(brokerQueueName, receiver);
		broker.run();

		// if we get here, it's time to exit
		System.exit(0);
	}
}
