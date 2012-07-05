/*
 * Copyright 2012 the original author or authors.
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
package org.powertac.samplebroker.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.IdGenerator;
import org.powertac.common.TimeService;
import org.powertac.common.Timeslot;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.common.msg.BrokerAccept;
import org.powertac.common.msg.BrokerAuthentication;
import org.powertac.common.msg.SimEnd;
import org.powertac.common.msg.SimPause;
import org.powertac.common.msg.SimResume;
import org.powertac.common.msg.SimStart;
import org.powertac.common.msg.TimeslotComplete;
import org.powertac.common.msg.TimeslotUpdate;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.common.spring.SpringApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * This is the top-level controller for the broker. It sets up the other
 * components, maintains the clock, and terminates when the SimEnd message
 * is received.
 * 
 * @author John Collins
 */
@Service
public class PowerTacBroker
implements BrokerContext
{
  static private Logger log = Logger.getLogger(PowerTacBroker.class);
  
  @Autowired
  private BrokerPropertiesService propertiesService;
  
  @Autowired
  private TimeService timeService;
  
  @Autowired
  private TimeslotRepo timeslotRepo;

  // Broker components
  @Autowired
  private MessageDispatcher router;
  
  @Autowired
  private JmsManagementService jmsManagementService;
  
  @Autowired
  private BrokerTournamentService brokerTournamentService;
  
  @Autowired
  private BrokerMessageReceiver brokerMessageReceiver;

  @Autowired
  private CustomerRepo customerRepo;

  /** parameters */
  // keep in mind that brokers need to deal with two viewpoints. Tariff
  // types take the viewpoint of the customer, while market-related types
  // take the viewpoint of the broker.
  private int usageRecordLength = 7 * 24; // one week
  
  @ConfigurableValue(valueType = "Integer",
      description = "Login retry timeout")
  private Integer loginRetryTimeout = 3000;

  @ConfigurableValue(valueType = "String",
          description = "Broker username")
  private String username = "broker";

  @ConfigurableValue(valueType = "String",
          description = "Broker login password")
  private String password = "password";

  @ConfigurableValue(valueType = "String",
          description = "Name of tournament")
  private String tourneyName = "";

  @ConfigurableValue(valueType = "String",
          description = "url for tournament login")
  private String tourneyUrl = "";

  @ConfigurableValue(valueType = "String",
          description = "Authorization token for tournament")
  private String authToken = "";

  // Broker keeps its own records
  private ArrayList<String> brokerNames;
  private Instant baseTime = null;
  private long quittingTime = 0l;
  private int currentTimeslot = 0; // index of last started timeslot
  private int timeslotCompleted = 0; // index of last completed timeslot
  private boolean running = false; // true to run, false to stop
  private BrokerAdapter adapter;
  private String serverQueueName = "serverInput";
  private String brokerQueueName; // set by tournament manager
  
  // needed for backward compatibility
  private String jmsBrokerUrl = null;

  /**
   * Default constructor for remote broker deployment
   */
  public PowerTacBroker ()
  {
    super();
  }
  
  public void startSession (File configFile, String jmsUrl, long end)
  {
    quittingTime = end;
    if (null != configFile && configFile.canRead())
      propertiesService.setUserConfig(configFile);
    if (null != jmsUrl)
      propertiesService.setProperty("samplebroker.core.jmsManagementService.jmsBrokerUrl",
                                      jmsUrl);
    propertiesService.configureMe(this);
    
    // Initialize and run.
    init();
    run();
  }

  /**
   * Sets up the "adapter" broker, initializes the other services, registers
   * for incoming messages.
   */
  @SuppressWarnings("unchecked")
  public void init ()
  {
    adapter = new BrokerAdapter(username);

    // Set up components
    brokerNames = new ArrayList<String>();
    
    // initialize services
    List<Initializable> initializers =
        SpringApplicationContext.listBeansOfType(Initializable.class);
    for (Initializable svc : initializers) {
      svc.initialize(this);
    }

    // register message handlers
    for (Class<?> clazz: Arrays.asList(BrokerAccept.class,
                                       Competition.class,
                                       SimEnd.class,
                                       SimPause.class,
                                       SimResume.class,
                                       SimStart.class,
                                       TimeslotComplete.class,
                                       TimeslotUpdate.class)) {
      router.registerMessageHandler(this, clazz);
    }
  }

  /**
   * Logs in and waits for the sim to end.
   */
  public void run ()
  {
    brokerQueueName = username;
    // log into the tournament manager if tourneyUrl is non-empty
    if (null != tourneyUrl && !tourneyUrl.isEmpty() &&
            brokerTournamentService.login(tourneyName,
                                          tourneyUrl,
                                          authToken,
                                          quittingTime)) {
        jmsBrokerUrl = brokerTournamentService.getJmsUrl();
        serverQueueName = brokerTournamentService.getServerQueueName();
        brokerQueueName = brokerTournamentService.getBrokerQueueName();
    }
    
    // wait for the JMS broker to show up and create our queue
    
    adapter.setQueueName(brokerQueueName);
    // if null, assume local broker without jms connectivity
    jmsManagementService.init(jmsBrokerUrl, serverQueueName);
    jmsManagementService.registerMessageListener(brokerMessageReceiver,
                                                 brokerQueueName);
    
    // Log in to server.
    // In case the server does not respond within  second
    BrokerAuthentication auth =
            new BrokerAuthentication(username, password,
                                     adapter.toQueueName());
    synchronized(this) {
      while (!adapter.isEnabled()) {
        try {
          sendMessage(auth);
          wait(loginRetryTimeout);
        }
        catch (InterruptedException e) {
          log.warn("Interrupted!");
          break;
        }
        catch (Exception ex) {
          log.info("log attempt failed " + ex.toString());
          try {
            Thread.sleep(loginRetryTimeout);
          }
          catch (InterruptedException e) {
            // ignore
          }
        }
      }
    }
    
    // start the activation thread
    BrokerRunner runner = new BrokerRunner(this);
    runner.start();
    try {
      runner.join();
    }
    catch (InterruptedException ie) {
      log.warn("Interrupted!");
    }
    jmsManagementService.shutdown();
  }

//  private String generateQueueName ()
//  {
//    long time = new Date().getTime() & 0xffffffff;
//    long ran = (long)(time * (Math.random() + 0.5));
//    return adapter.getUsername() + "." + Long.toString(ran, 31);
//  }
  
  // ------------- Accessors ----------------
//  /**
//   * Returns the message router
//   */
//  public MessageDispatcher getRouter ()
//  {
//    return router;
//  }
  
  /**
   * Returns the "real" broker underneath this monstrosity
   */
  @Override
  public Broker getBroker ()
  {
    return adapter;
  }
  
  /**
   * Returns the username for this broker
   */
  @Override
  public String getBrokerUsername ()
  {
    return adapter.getUsername();
  }
  
//  /**
//   * Returns the customerRepo. Cannot be called until after initialization.
//   */
//  public CustomerRepo getCustomerRepo ()
//  {
//    return customerRepo;
//  }

  /**
   * Returns the simulation base time
   */
  @Override
  public Instant getBaseTime()
  {
    return baseTime;
  }
  
  /**
   * Returns the length of the standard data array (24h * 7d)
   */
  @Override
  public int getUsageRecordLength ()
  {
    return usageRecordLength;
  }
  
  /**
   * Returns the broker's list of competing brokers - non-public
   */
  @Override
  public List<String> getBrokerList ()
  {
    return brokerNames;
  }
  
  /**
   * Delegates registrations to the router
   */
  @Override
  public void registerMessageHandler (Object handler, Class<?> messageType)
  {
    router.registerMessageHandler(handler, messageType);
  }

  // ------------ process messages -------------
  /**
   * Incoming messages for brokers include:
   * <ul>
   * <li>TariffTransaction tells us about customer subscription
   *   activity and power usage,</li>
   * <li>MarketPosition tells us how much power we have bought
   *   or sold in a given timeslot,</li>
   * <li>CashPosition tells us it's time to send in our bids/asks</li>
   * </ul>
   */

  /**
   * Sends an outgoing message. May need to be reimplemented in a remote broker.
   */
  @Override
  public void sendMessage (Object message)
  {
    if (message != null) {
      //brokerProxyService.routeMessage(message);
      router.sendMessage(message);
    }
  }

  // -------------------- message handlers ---------------------
  //
  // Note that these arrive in JMS threads; If they share data with the
  // agent processing thread, they need to be synchronized.
  
  /**
   * BrokerAccept comes out when our authentication credentials are accepted
   * and we become part of the game. Before this, we cannot send any messages
   * other than BrokerAuthentication. Also, note that the ID prefix needs to be
   * set before any server-visible entities are created (such as tariff specs).
   */
  public synchronized void handleMessage (BrokerAccept accept)
  {
    adapter.setEnabled(true);
    IdGenerator.setPrefix(accept.getPrefix());
    //adapter.setKey(accept.getKey());
    //router.setKey(accept.getKey());
    notifyAll();
  }
  
  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture all the customer records so we can keep track of their
   * subscriptions and usage profiles.
   */
  public void handleMessage (Competition comp)
  {
    // comp needs to be the "current competition"
    Competition.setCurrent(comp);
    
    // record the customers and brokers
    for (CustomerInfo customer : comp.getCustomers()) {
      customerRepo.add(customer);
    }
    for (String brokerName : comp.getBrokers()) {
      brokerNames.add(brokerName);
    }
    // in a remote broker, we pull out the clock
    // parameters to init the local clock, and create the initial timeslots.
    Instant bootBaseTime = comp.getSimulationBaseTime();
    int bootTimeslotCount =
        (int)(comp.getBootstrapTimeslotCount() + 
              comp.getBootstrapDiscardedTimeslots());
    for (int sn = 0; sn <= bootTimeslotCount; sn++) {
      Timeslot slot =
          timeslotRepo.makeTimeslot(bootBaseTime.plus(sn * comp.getTimeslotDuration()));
      slot.disable();
    }
    // now set time to end of bootstrap period.
    timeService.setClockParameters(comp.getClockParameters());
    log.info("Sim start time: " + timeService.getCurrentDateTime().toString());
  }

  /**
   * Receives the SimPause message, used to pause the clock.
   */
  public void handleMessage (SimPause sp)
  {
    // local brokers can ignore this.
    log.info("Paused at " + timeService.getCurrentDateTime().toString());
  }
  
  /**
   * Receives the SimResume message, used to update the clock.
   */
  public void handleMessage (SimResume sr)
  {
    // local brokers don't need to handle this
    log.info("Resumed");
    timeService.setStart(sr.getStart().getMillis());
    timeService.updateTime();
  }
  
  /**
   * Receives the SimStart message, used to start the clock.
   */
  public void handleMessage (SimStart ss)
  {
    // local brokers don't need to do anything with this.
    log.info("SimStart - start time is " + ss.getStart().toString());
    timeService.setStart(ss.getStart().getMillis());
    timeService.updateTime();
    log.info("SimStart - clock set to " + timeService.getCurrentDateTime().toString());
  }
  
  /**
   * Receives the SimEnd message, which ends the broker session.
   */
  public synchronized void handleMessage (SimEnd se)
  {
    // local brokers can ignore this
    log.info("SimEnd received");
    running = false;
    notifyAll();
  }
  
  /**
   * Updates the sim clock on receipt of the TimeslotUpdate message,
   * which should be the first to arrive in each timeslot. We have to disable
   * all the timeslots prior to the first enabled slot, then create and enable
   * all the enabled slots.
   */
  public synchronized void handleMessage (TimeslotUpdate tu)
  {
    Timeslot old = timeslotRepo.currentTimeslot();
    timeService.updateTime(); // here is the clock update
    log.info("TimeslotUpdate at " + timeService.getCurrentDateTime().toString());
    //List<Timeslot> enabled = tu.getEnabled();
    for (int index = old.getSerialNumber();
         index < tu.getFirstEnabled();
         index ++) {
      Timeslot closed = 
          timeslotRepo.findOrCreateBySerialNumber(index);
      closed.disable();
      currentTimeslot = index;
    }
    for (int index = tu.getFirstEnabled();
         index <= tu.getLastEnabled();
         index++) {
      Timeslot open =
          timeslotRepo.findOrCreateBySerialNumber(index);
      open.enable();
    }
  }
  
  /**
   * CashPosition is the last message sent by Accounting.
   * This is normally when any broker would submit its bids, so that's when
   * this Broker will do it.
   */
  public synchronized void handleMessage (TimeslotComplete tc)
  {
    if (tc.getTimeslotIndex() == currentTimeslot) {
      timeslotCompleted = currentTimeslot;
      notifyAll();
    }
    else {
      // missed a timeslot
      log.warn("Skipped timeslot " + tc.getTimeslotIndex());
    }
  }

  // The worker thread comes here to wait for the next activation
  synchronized int waitForActivation (int index)
  {
    try {
      while (running && (timeslotCompleted <= index))
        wait();
    }
    catch (InterruptedException ie) {
      log.warn("activation interrupted: " + ie);
    }
    return timeslotCompleted;
  }
  
  /**
   * Thread to encapsulate internal broker operations, allowing JMS threads
   * to return quickly and stay in sync with the server. 
   */
  class BrokerRunner extends Thread
  {
    PowerTacBroker parent;
    int timeslotIndex = 0;
    
    public BrokerRunner (PowerTacBroker parent)
    {
      super();
      this.parent = parent;
    }
    
    /**
     * In each timeslot, we must update our portfolio and trade in the 
     * wholesale market.
     */
    @Override
    public void run ()
    {
      running = true;

      while (true) {
        timeslotIndex = waitForActivation(timeslotIndex);
        if (!running) {
          log.info("worker thread exits at ts " + timeslotIndex);
          return;
        }
        
        Timeslot current = timeslotRepo.currentTimeslot();
        log.info("activate at " + timeService.getCurrentDateTime().toString()
                 + ", timeslot " + current.getSerialNumber());
        List<Activatable> services =
            SpringApplicationContext.listBeansOfType(Activatable.class);
        for (Activatable svc : services) {
          if (timeslotIndex < currentTimeslot) {
            log.warn("broker late, ts="+ timeslotIndex);
            break;
          }
          svc.activate(timeslotIndex);
        }
      }
    }
  }
  
  /**
   * Broker implementation needed to override the receiveMessage method.
   */
  class BrokerAdapter extends Broker
  {

    public BrokerAdapter (String username)
    {
      super(username);
    }

    /**
     * Here is where incoming messages actually arrive.
     */
    @Override
    public void receiveMessage (Object msg)
    {
      //log.info("receive " + msg.toString());
      if (msg != null) {
        // ignore all incoming messages until enabled.
        if (!(isEnabled() || msg instanceof BrokerAccept))
          return;
        router.routeMessage(msg);
      }
    }
    
  }
}
