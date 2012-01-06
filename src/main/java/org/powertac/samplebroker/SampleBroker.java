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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.CashPosition;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.RandomSeed;
import org.powertac.common.Timeslot;
import org.powertac.common.interfaces.BrokerProxy;
import org.powertac.common.msg.BrokerAccept;
import org.powertac.common.msg.BrokerAuthentication;
import org.powertac.common.msg.SimPause;
import org.powertac.common.msg.SimResume;
import org.powertac.common.msg.SimStart;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.common.spring.SpringApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * @author John Collins
 */
public class SampleBroker extends Broker
{
  static private Logger log = Logger.getLogger(SampleBroker.class);

  // Services that need to be replicated in a remote broker
  //@Autowired
  private BrokerProxy brokerProxyService;
  
  //@Autowired
  private TimeslotRepo timeslotRepo;

  // Broker components
  private MessageDispatcher router;
  
  //@Autowired
  private PortfolioManagerService portfolioManagerService;
  
  //@Autowired
  private MarketManagerService marketManagerService;

  /** parameters */
  // keep in mind that brokers need to deal with two viewpoints. Tariff
  // types take the viewpoint of the customer, while market-related types
  // take the viewpoint of the broker.
  private int usageRecordLength = 7 * 24; // one week
  
  // local state
  private RandomSeed randomSeed;

  // Broker keeps its own records
  private CustomerRepo customerRepo;
  private ArrayList<String> brokerNames;
  private Instant baseTime = null;
  
  private SampleBrokerService service;

  /**
   * Constructor for local broker deployment takes a username and a 
   * reference to the service
   */
  public SampleBroker (String username, SampleBrokerService service)
  {
    super(username);
    setLocal(true);
    this.service = service;
  }
  
  /**
   * Called by initialization service once at the beginning of each game.
   */
  @SuppressWarnings("unchecked")
  void init ()
  {
    log.info("initialize: local=" + isLocal());

    // fill in service references
    brokerProxyService =
        (BrokerProxy) SpringApplicationContext.getBean("brokerProxyService");
    timeslotRepo =
        (TimeslotRepo) SpringApplicationContext.getBean("timeslotRepo");
    router = new MessageDispatcher(); // must be set up first
    portfolioManagerService =
        (PortfolioManagerService) SpringApplicationContext.getBean("portfolioManagerService");
    marketManagerService =
        (MarketManagerService) SpringApplicationContext.getBean("marketManagerService");

    // set up local state
    customerRepo = new CustomerRepo();
    brokerNames = new ArrayList<String>();
    randomSeed =
        service.getRandomSeedRepo().getRandomSeed(this.getClass().getName(),
                                                  0, getUsername());

    // Set up components
    portfolioManagerService.init(this);
    marketManagerService.init(this);
    for (Class<?> clazz: Arrays.asList(BrokerAccept.class,
                                       CashPosition.class,
                                       Competition.class,
                                       SimPause.class,
                                       SimResume.class,
                                       SimStart.class)) {
      router.registerMessageHandler(this, clazz);
    }

    // log in to ccs
    sendMessage(new BrokerAuthentication(this));
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
   * Returns the customerRepo. Cannot be called until after initialization.
   */
  public CustomerRepo getCustomerRepo ()
  {
    return customerRepo;
  }
  
  public RandomSeed getRandomSeed ()
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

  /**
   * Sends an outgoing message. May need to be reimplemented in a remote broker.
   */
  public void sendMessage (Object message)
  {
    if (message != null) {
      brokerProxyService.routeMessage(message);
    }
  }

  // -------------------- message handlers ---------------------
  /**
   * BrokerAccept comes out when our authentication credentials are accepted
   * and we become part of the game. Before this, we cannot send any messages
   * other than BrokerAuthentication.
   */
  public void handleMessage (BrokerAccept accept)
  {
    setEnabled(true);
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
    for (CustomerInfo customer : comp.getCustomers()) {
      customerRepo.add(customer);
    }
    for (String brokerName : comp.getBrokers()) {
      brokerNames.add(brokerName);
    }
    // in a remote broker, we would also need to pull out the clock
    // parameters to init the local clock, and create the initial timeslots.
    baseTime = comp.getSimulationBaseTime();
  }
  
  /**
   * Receives the SimPause message, used to pause the clock.
   */
  public void handleMessage (SimPause sp)
  {
    // local brokers can ignore this.
  }
  
  /**
   * Receives the SimResume message, used to update the clock.
   */
  public void handleMessage (SimResume sr)
  {
    // local brokers don't need to handle this
  }
  
  /**
   * Receives the SimStart message, used to start the clock.
   */
  public void handleMessage (SimStart ss)
  {
    // local brokers don't need to do anything with this.
  }

  //double consumption = (config.getDoubleValue("consumptionRate",
  //                                            defaultConsumptionRate));
  //defaultConsumption = new TariffSpecification(face, PowerType.CONSUMPTION)
  //.addRate(new Rate().withValue(consumption));
  //double production = (config.getDoubleValue("productionRate",
  //                                           defaultProductionRate));
  //defaultProduction = new TariffSpecification(face, PowerType.PRODUCTION)
  //   .addRate(new Rate().withValue(production));
  /**
   * In each timeslot, we must trade in the wholesale market to satisfy the
   * predicted load of our current customer base.
   */
  public void activate ()
  {
    Timeslot current = timeslotRepo.currentTimeslot();
    log.info("activate: timeslot " + current.getSerialNumber());
    portfolioManagerService.activate();
    marketManagerService.activate();
  }
  
  // ----------------- test support ------------------
  List<String> getBrokerList ()
  {
    return brokerNames;
  }
  
  List<CustomerInfo> getCustomerList ()
  {
    return new ArrayList<CustomerInfo>(customerRepo.list());
  }
}
