package org.powertac.samplebroker;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.powertac.common.config.ConfigurableValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

@Service
public class BrokerTournamentService {

	static private Logger log = Logger.getLogger(BrokerMessageReceiver.class);

	@Autowired
	private BrokerPropertiesService brokerPropertiesService;

	// The game specific token is only valid for one game
	private String gameToken = null;
	private String jmsUrl = null;

	// Configurable parameters
	private String authToken = null;
	private String tourneyName = null;
	private String responseType = null;
	private String username = null;

	// If set to negative number infinite retries
	private int maxTry = 1;

	public void init() {
		brokerPropertiesService.configureMe(this);
	}

	public String getUsername() {
		return username;
	}

	@ConfigurableValue(valueType = "String", description = "Set the username of broker for tournament")
	public void setUsername(String username) {
		this.username = username;
	}

	public String getResponseType() {
		return responseType;
	}

	@ConfigurableValue(valueType = "String", description = "Response type to receive from the TS xml or json")
	public void setResponseType(String responseType) {
		this.responseType = responseType;
	}

	public String getTourneyName() {
		return tourneyName;
	}

	@ConfigurableValue(valueType = "String", description = "Name of tournament to join")
	public void setTourneyName(String tourneyName) {
		this.tourneyName = tourneyName;
	}

	public int getMaxTry() {
		return maxTry;
	}

	@ConfigurableValue(valueType = "Integer", description = "Maximum number of tries to connect to Tournament Scheduler")
	public void setMaxTry(int maxTry) {
		this.maxTry = maxTry;
	}

	public String getAuthToken() {
		return authToken;
	}

	@ConfigurableValue(valueType = "String", description = "Broker unique authorization token to authenticate with Tournament Scheduler")
	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public String getGameToken() {
		return gameToken;
	}

	public void setGameToken(String gameToken) {
		this.gameToken = gameToken;
	}

	// Spins current login attemt for n seconds and url to retry
	private void spin(int seconds) {
		try {

			Thread.sleep(4 * 1000);
		} catch (InterruptedException e) {
			// unable to sleep
			e.printStackTrace();
		}
	}

	private boolean loginMaybe(String tsUrl) {

		try {
			// Build proper connection string to tournament scheduler for
			// login
			String restAuthToken = "authToken=" + this.authToken + "&";
			String restTourneyName = "requestJoin=" + this.tourneyName + "&";
			String restResponseType = "type=" + this.responseType;
			String finalUrl = tsUrl + "?" + restAuthToken + restTourneyName
					+ restResponseType;
			log.info("Connecting to TS with " + finalUrl);

			URL url = new URL(finalUrl);
			URLConnection conn = url.openConnection();

			// Get the response
			InputStream input = conn.getInputStream();

			if (this.responseType.compareTo("xml") == 0) {

				DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
						.newInstance();
				DocumentBuilder docBuilder = docBuilderFactory
						.newDocumentBuilder();
				Document doc = docBuilder.parse(input);

				doc.getDocumentElement().normalize();

				// Three different message types
				Node retryNode = doc.getElementsByTagName("retry").item(0)
						.getFirstChild();
				// Node loginNode =
				// doc.getElementsByTagName("login").item(0).getFirstChild();
				// Node doneNode =
				// doc.getElementsByTagName("done").item(0).getFirstChild();

				String checkRetry = retryNode.getNodeValue();
				// String checkLogin = loginNode.getNodeValue();
				// String checkDone = doneNode.getNodeValue();

				if (checkRetry != null) {
					log.info("Retry message received for : " + checkRetry
							+ " seconds");
					System.out.println("Retry message received for : "
							+ checkRetry + " seconds");
					// Received retry message spin and try again
					spin(Integer.parseInt(checkRetry));
					return false;
				} else {
					log.fatal("Invalid message type recieved");
					return false;

				}

				// TODO parse login success
				// TODO parse done success
			} else { // response type was json parse accordingly
				// TODO: Json Parsing
			}

			return true;
		} catch (Exception e) { // exception hit return false
			maxTry--;
			System.out.println("Retries left: " + maxTry);
			log.fatal("Error making connection to Tournament Scheduler");
			log.fatal(e.getMessage());
			// Sleep and wait for network
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
				
				e1.printStackTrace();
				return false;
			}
			return false;
		}

	}

	// Returns the game token on success, null on failure
	public String login(String tsUrl) {
		if (this.authToken != null && tsUrl != null) {
			while (maxTry > 0) {
				System.out.println("Connecting...");
				if (loginMaybe(tsUrl)) {
					log.info("Login Successful! Game token: " + this.gameToken);
					return this.jmsUrl;
				}
			}
			System.out.println("Max attempts reached...shutting down");
			log.fatal("Max attempts to log in reached");
			System.exit(0);
		} else {
			log.fatal("Incorrect Tournament Scheduler URL or Broker Auth Token");
			System.exit(0);
		}
		return null;
	}

}
