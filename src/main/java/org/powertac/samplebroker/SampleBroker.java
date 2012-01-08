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
package org.powertac.samplebroker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.CashPosition;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.IdGenerator;
import org.powertac.common.TimeService;
import org.powertac.common.Timeslot;
import org.powertac.common.msg.BrokerAccept;
import org.powertac.common.msg.BrokerAuthentication;
import org.powertac.common.msg.SimEnd;
import org.powertac.common.msg.SimPause;
import org.powertac.common.msg.SimResume;
import org.powertac.common.msg.SimStart;
import org.powertac.common.msg.TimeslotUpdate;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
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
public class SampleBroker
{
  static private Logger log = Logger.getLogger(SampleBroker.class);

  // Services that need to be replicated in a remote broker
  //@Autowired
  //private BrokerProxy brokerProxyService;
  
  @Autowired
  private TimeService timeService;
  
  @Autowired
  private TimeslotRepo timeslotRepo;

  // Broker components
  @Autowired
  private MessageDispatcher router;
  
  @Autowired
  private PortfolioManagerService portfolioManagerService;
  
  @Autowired
  private MarketManagerService marketManagerService;

  @Autowired
  private CustomerRepo customerRepo;

  /** parameters */
  // keep in mind that brokers need to deal with two viewpoints. Tariff
  // types take the viewpoint of the customer, while market-related types
  // take the viewpoint of the broker.
  private int usageRecordLength = 7 * 24; // one week
  
  // local state
  private Random randomSeed = new Random();

  // Broker keeps its own records
  private ArrayList<String> brokerNames;
  private Instant baseTime = null;
  private BrokerAdapter adapter;
  private boolean running = false; // true to run, false to stop
  
  //private SampleBrokerService service;

  /**
   * Default constructor for remote broker deployment
   */
  public SampleBroker ()
  {
    super();
  }
  
  /**
   * Sets up the "adapter" broker, initializes the other services, registers
   * for incoming messages.
   */
  @SuppressWarnings("unchecked")
  public void init (String username)
  {
    adapter = new BrokerAdapter(username);

    // Set up components
    brokerNames = new ArrayList<String>();
    portfolioManagerService.init(this);
    marketManagerService.init(this);
    for (Class<?> clazz: Arrays.asList(BrokerAccept.class,
                                       CashPosition.class,
                                       Competition.class,
                                       SimEnd.class,
                                       SimPause.class,
                                       SimResume.class,
                                       SimStart.class,
                                       TimeslotUpdate.class)) {
      router.registerMessageHandler(this, clazz);
    }
  }
  
  /**
   * Logs in and waits for the sim to end.
   */
  public void run ()
  {
    // log in to server
    sendMessage(new BrokerAuthentication(adapter.getUsername(), "blank"));

    // wait for session to end
    synchronized (this) {
      running = true;
      while (running) {
        try {
          wait();
        }
        catch (InterruptedException ie) {
          log.warn("Interrupted!");
        }
      }
    }
  }
  
  // ------------- Accessors ----------------
  /**
   * Returns the message router
   */
  public MessageDispatcher getRouter ()
  {
    return router;
  }
  
  /**
   * Returns the "real" broker underneath this monstrosity
   */
  public Broker getBroker ()
  {
    return adapter;
  }
  
  /**
   * Returns the username for this broker
   */
  public String getBrokerUsername ()
  {
    return adapter.getUsername();
  }
  
  /**
   * Returns the customerRepo. Cannot be called until after initialization.
   */
  public CustomerRepo getCustomerRepo ()
  {
    return customerRepo;
  }
  
  public Random getRandomSeed ()
  {
    return randomSeed;
  }

  /**
   * Returns the simulation base time
   */
  public Instant getBaseTime()
  {
    return baseTime;
  }
  
  /**
   * Returns the length of the standard data array (24h * 7d)
   */
  public int getUsageRecordLength ()
  {
    return usageRecordLength;
  }
  
  /**
   * Returns the broker's list of competing brokers - non-public
   */
  List<String> getBrokerList ()
  {
    return brokerNames;
  }
  
  /**
   * Delegates registrations to the router
   */
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
  public void sendMessage (Object message)
  {
    if (message != null) {
      //brokerProxyService.routeMessage(message);
      router.sendMessage(message);
    }
  }

  // -------------------- message handlers ---------------------
  /**
   * BrokerAccept comes out when our authentication credentials are accepted
   * and we become part of the game. Before this, we cannot send any messages
   * other than BrokerAuthentication. Also, note that the ID prefix needs to be
   * set before any server-visible entities are created (such as tariff specs).
   */
  public void handleMessage (BrokerAccept accept)
  {
    adapter.setEnabled(true);
    IdGenerator.setPrefix(accept.getPrefix());
  }
  
  /**
   * CashPosition is the last message sent by Accounting.
   * This is normally when any broker would submit its bids, so that's when
   * this Broker will do it.
   */
  public void handleMessage (CashPosition cp)
  {
    this.activate();
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
    baseTime = comp.getSimulationBaseTime();
    timeService.setClockParameters(comp.getSimulationBaseTime().getMillis(),
                                   comp.getSimulationRate(), 
                                   comp.getSimulationModulo());
    timeService.init(); // set time to beginning of bootstrap period
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
    // create initial timeslots
    timeslotRepo.createInitialTimeslots();
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
  public void handleMessage (TimeslotUpdate tu)
  {
    Timeslot old = timeslotRepo.currentTimeslot();
    timeService.updateTime(); // here is the clock update
    log.info("TimeslotUpdate at " + timeService.getCurrentDateTime().toString());
    List<Timeslot> enabled = tu.getEnabled();
    for (int index = old.getSerialNumber();
         index < enabled.get(0).getSerialNumber();
         index ++) {
      timeslotRepo.findBySerialNumber(index).disable();
    }
    for (Timeslot ts : tu.getEnabled()) {
      Timeslot open =
          timeslotRepo.findOrCreateBySerialNumber(ts.getSerialNumber());
      open.enable();
    }
  }

  /**
   * In each timeslot, we must update our portfolio and trade in the 
   * wholesale market.
   */
  public void activate ()
  {
    Timeslot current = timeslotRepo.currentTimeslot();
    log.info("activate: timeslot " + current.getSerialNumber());
    portfolioManagerService.activate();
    marketManagerService.activate();
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
