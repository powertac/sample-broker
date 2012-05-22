/*
 * Copyright (c) 2012 by the original author
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.powertac.samplebroker.core;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.powertac.common.config.ConfigurableValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
/**
 * @author Erik Onarheim
 */
@Service
public class BrokerTournamentService
{
  static private Logger log = Logger.getLogger(BrokerMessageReceiver.class);

  @Autowired
  private BrokerPropertiesService brokerPropertiesService;

  // The game specific token is only valid for one game
  private String gameToken = null;
  private String jmsUrl = null;

  // Configurable parameters
  private String authToken = null;
  private String tourneyName = null;
  private String responseType = "xml";
  //private String username = null;

  // If set to negative number infinite retries
  private int maxTry = 5;

  public void init()
  {
    brokerPropertiesService.configureMe(this);
  }

  public String getResponseType()
  {
    return responseType;
  }

  @ConfigurableValue(valueType = "String", description = "Response type to receive from the TS xml or json")
  public void setResponseType(String responseType)
  {
    this.responseType = responseType;
  }

  public int getMaxTry()
  {
    return maxTry;
  }

  @ConfigurableValue(valueType = "Integer", description = "Maximum number of tries to connect to Tournament Scheduler")
  public void setMaxTry(int maxTry)
  {
    this.maxTry = maxTry;
  }

  public String getGameToken()
  {
    return gameToken;
  }

  public void setGameToken(String gameToken)
  {
    this.gameToken = gameToken;
  }

  // Spins current login attemt for n seconds and url to retry
  private void spin(int seconds)
  {
    try {

      Thread.sleep(seconds * 1000);
    } catch (InterruptedException e)
    {
      // unable to sleep
      e.printStackTrace();
    }
  }

  private boolean loginMaybe(String tsUrl)
  {
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
        System.out.println("Parsing message..");
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder docBuilder = docBuilderFactory
                .newDocumentBuilder();
        Document doc = docBuilder.parse(input);

        doc.getDocumentElement().normalize();

        // Three different message types
        Node retryNode = doc.getElementsByTagName("retry").item(0);
        Node loginNode = doc.getElementsByTagName("login").item(0);
        Node doneNode = doc.getElementsByTagName("done").item(0);

        if (retryNode != null) {
          String checkRetry = retryNode.getFirstChild()
                  .getNodeValue();
          log.info("Retry message received for : " + checkRetry
                   + " seconds");
          System.out.println("Retry message received for : "
                  + checkRetry + " seconds");
          // Received retry message spin and try again
          spin(Integer.parseInt(checkRetry));
          return false;
        }
        else if (loginNode != null) {
          System.out.println("Login Message Received!");
          String checkJmsUrl = doc.getElementsByTagName("jmsUrl").item(0).getFirstChild().getNodeValue();
          String checkToken = doc.getElementsByTagName("gameToken").item(0).getFirstChild().getNodeValue();
          log.info("Login message received! ");
          log.info("jmsUrl="+checkJmsUrl);
          log.info("gameToken="+checkToken);

          System.out.printf("Login message receieved!\njmsUrl=%s\ngameToken=%s\n",checkJmsUrl,checkToken);
          this.jmsUrl = checkJmsUrl;
          this.gameToken = checkToken;
          return true;
        }
        else if (doneNode != null) {
          System.out.println("Recieved Done Message no more games!");
          maxTry=0;
          return false;
        }
        else {
          log.fatal("Invalid message type recieved");
          return false;
        }

        // TODO parse login success
        // TODO parse done success

      }
      else { // response type was json parse accordingly
        String jsonTxt = IOUtils.toString(input);

        JSONObject json = (JSONObject) JSONSerializer.toJSON(jsonTxt);
        int retry = json.getInt("retry");
        System.out.println("Retry message received for : " + retry
                           + " seconds");
        spin(retry);
        return false;

        // TODO: Good Json Parsing
      }
    }
    catch (Exception e) { // exception hit return false
      maxTry--;
      System.out.println("Retries left: " + maxTry);
      e.printStackTrace();
      log.fatal("Error making connection to Tournament Scheduler");
      log.fatal(e.getMessage());
      // Sleep and wait for network
      try {
        Thread.sleep(2000);
      }
      catch (InterruptedException e1) {
        e1.printStackTrace();
        return false;
      }
      return false;
    }

  }

  // Returns the game URL on success, dies on failure
  public String login(String tournamentName,
                      String tsUrl,
                      String authToken)
  {
    this.tourneyName = tournamentName;
    this.authToken = authToken;
    
    if (this.authToken != null && tsUrl != null) {
      while (maxTry > 0) {
        System.out.println("Connecting to TS...");
        if (loginMaybe(tsUrl)) {
          log.info("Login Successful! Game token: " + this.gameToken);
          return this.jmsUrl;
        }
      }
      System.out.println("Max attempts reached...shutting down");
      log.fatal("Max attempts to log in reached");
      System.exit(0);
    }
    else {
      log.fatal("Incorrect Tournament Scheduler URL or Broker Auth Token");
      System.exit(0);
    }
    return null;
  }
}
