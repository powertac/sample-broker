package org.powertac.samplebroker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

import org.springframework.stereotype.Service;

@Service
public class BrokerTournamentService {

	// The game specific token is only valid for one game
	private String gameToken = null;

	// Configurable parameters
	private String authToken = null;
	private String tourneyName = null;
	private String responseType = null;
	private int maxTry = 1;

	public String getResponseType() {
		return responseType;
	}

	public void setResponseType(String responseType) {
		this.responseType = responseType;
	}

	public String getTourneyName() {
		return tourneyName;
	}

	public void setTourneyName(String tourneyName) {
		this.tourneyName = tourneyName;
	}

	public int getMaxTry() {
		return maxTry;
	}

	public void setMaxTry(int maxTry) {
		this.maxTry = maxTry;
	}

	public String getAuthToken() {
		return authToken;
	}

	public void setAuthToken(String authToken) {
		this.authToken = authToken;
	}

	public String getGameToken() {
		return gameToken;
	}

	public void setGameToken(String gameToken) {
		this.gameToken = gameToken;
	}

	private boolean loginMaybe(String tsUrl) {

		try {
			String restAuthToken = "authToken=" + this.authToken + "&";
			String restTourneyName = "requestJoin=" + this.tourneyName + "&";
			String restResponseType = "type=" + this.responseType;
			URL url = new URL(tsUrl + "?" + restAuthToken + restTourneyName
					+ restResponseType);
			URLConnection conn = url.openConnection();

			// Get the response
			BufferedReader input = new BufferedReader(new InputStreamReader(
					conn.getInputStream()));

			return true;
		} catch (Exception e) {

			return false;
		}

	}

	public String login(String tsUrl) {
		if (this.authToken != null && tsUrl != null) {
			if (tsUrl.indexOf("brokerLogin.jsp") > 0) {
				while (!loginMaybe(tsUrl) && maxTry > 0) {
					maxTry--;
				}
			} else {
				return null;
			}
		}
		return tsUrl;
	}

}
