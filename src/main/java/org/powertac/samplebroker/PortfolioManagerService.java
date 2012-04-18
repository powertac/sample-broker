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
package org.powertac.samplebroker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.Tariff;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.BalancingOrder;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles portfolio-management responsibilities for the broker. This
 * includes 
 * @author John Collins
 */
@Service
public class PortfolioManagerService 
implements PortfolioManager, Initializable, Activatable
{
  static private Logger log = Logger.getLogger(PortfolioManagerService.class);
  
  private BrokerContext broker; // master
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private TariffRepo tariffRepo;
  
  @Autowired
  private CustomerRepo customerRepo;
  
  @Autowired
  private MarketManager marketManager;
  
  @Autowired
  private TimeService timeService;

  // ---- Portfolio records -----
  // Customer records indexed by power type and by tariff. Note that the
  // CustomerRecord instances are NOT shared between these structures, because
  // we need to keep track of subscriptions by tariff.
  private HashMap<PowerType,
                  HashMap<CustomerInfo, CustomerRecord>> customerProfiles;
  private HashMap<TariffSpecification, 
                  HashMap<CustomerInfo, CustomerRecord>> customerSubscriptions;
  private HashMap<PowerType, List<TariffSpecification>> competingTariffs;

  // parameters
  private double defaultMargin = 0.5;
  private double fixedPerKwh = -0.06;
  private double defaultPeriodicPayment = -1.0;
  
  /**
   * Default constructor registers for messages, must be called after 
   * message router is available.
   */
  public PortfolioManagerService ()
  {
    super();
  }

  /**
   * Sets up message handling
   */
  @Override
  @SuppressWarnings("unchecked")
  public void initialize (BrokerContext broker)
  {
    this.broker = broker;
    customerProfiles = new HashMap<PowerType,
        HashMap<CustomerInfo, CustomerRecord>>();
    customerSubscriptions = new HashMap<TariffSpecification,
        HashMap<CustomerInfo, CustomerRecord>>();
    competingTariffs = new HashMap<PowerType, List<TariffSpecification>>();
    for (Class<?> messageType: Arrays.asList(CustomerBootstrapData.class,
                                             TariffSpecification.class,
                                             TariffStatus.class,
                                             TariffTransaction.class)) {
      broker.registerMessageHandler(this, messageType);
    }
  }
  
  // -------------- data access ------------------
  
  /**
   * Returns the CustomerRecord for the given type and customer, creating it
   * if necessary.
   */
  CustomerRecord getCustomerRecordByPowerType (PowerType type,
                                               CustomerInfo customer)
  {
    HashMap<CustomerInfo, CustomerRecord> customerMap =
        customerProfiles.get(type);
    if (customerMap == null) {
      customerMap = new HashMap<CustomerInfo, CustomerRecord>();
      customerProfiles.put(type, customerMap);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null) {
      record = new CustomerRecord(customer);
      customerMap.put(customer, record);
    }
    return record;
  }
  
  /**
   * Returns the customer record for the given tariff spec and customer,
   * creating it if necessary. 
   */
  CustomerRecord getCustomerRecordByTariff (TariffSpecification spec,
                                            CustomerInfo customer)
  {
    HashMap<CustomerInfo, CustomerRecord> customerMap =
        customerSubscriptions.get(spec);
    if (customerMap == null) {
      customerMap = new HashMap<CustomerInfo, CustomerRecord>();
      customerSubscriptions.put(spec, customerMap);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null) {
      // seed with the generic record for this customer
      record =
          new CustomerRecord(getCustomerRecordByPowerType(spec.getPowerType(),
                                                          customer));
      customerMap.put(customer, record);
    }
    return record;
  }
  
  /**
   * Finds the list of competing tariffs for the given PowerType.
   */
  List<TariffSpecification> getCompetingTariffs (PowerType powerType)
  {
    List<TariffSpecification> result = competingTariffs.get(powerType);
    if (result == null) {
      result = new ArrayList<TariffSpecification>();
      competingTariffs.put(powerType, result);
    }
    return result;
  }

  /**
   * Adds a new competing tariff to the list.
   */
  private void addCompetingTariff (TariffSpecification spec)
  {
    getCompetingTariffs(spec.getPowerType()).add(spec);
  }

  /**
   * Returns total usage for a given timeslot (represented as a simple index).
   */
  @Override
  public double collectUsage (int index)
  {
    double result = 0.0;
    for (HashMap<CustomerInfo, CustomerRecord> customerMap : customerSubscriptions.values()) {
      for (CustomerRecord record : customerMap.values()) {
        result += record.getUsage(index);
      }
    }
    return -result; // convert to needed energy account balance
  }

  // -------------- Message handlers -------------------
  /**
   * Handles CustomerBootstrapData by populating the customer model 
   * corresponding to the given customer and power type. This gives the
   * broker a running start.
   */
  public void handleMessage (CustomerBootstrapData cbd)
  {
    CustomerInfo customer =
            customerRepo.findByNameAndPowerType(cbd.getCustomerName(),
                                                cbd.getPowerType());
    CustomerRecord record = getCustomerRecordByPowerType(cbd.getPowerType(), customer);
    int offset = (timeslotRepo.currentTimeslot().getSerialNumber()
                  - cbd.getNetUsage().length);
    int subs = record.subscribedPopulation;
    record.subscribedPopulation = customer.getPopulation();
    for (int i = 0; i < cbd.getNetUsage().length; i++) {
      record.produceConsume(cbd.getNetUsage()[i], i + offset);
    }
    record.subscribedPopulation = subs;
  }

  /**
   * Handles a TariffSpecification. These are sent out when new tariffs are
   * published. If it's not ours, then it's a competitor.
   */
  public void handleMessage (TariffSpecification spec)
  {
    Broker theBroker = spec.getBroker();
    if (broker.getBrokerUsername() == theBroker.getUsername()) {
      // if it's ours, just log it
      log.info("published " + spec);
    }
    else {
      // otherwise, keep track
      addCompetingTariff(spec);
    }
  }
  
  /**
   * Handles a TariffStatus message.
   */
  public void handleMessage (TariffStatus ts)
  {
    log.info("TariffStatus: " + ts.getStatus());
  }
  
  /**
   * Handles a TariffTransaction. We only care about certain types: PRODUCE,
   * CONSUME, SIGNUP, and WITHDRAW.
   */
  public void handleMessage(TariffTransaction ttx)
  {
    TariffTransaction.Type txType = ttx.getTxType();
    CustomerRecord record = getCustomerRecordByTariff(ttx.getTariffSpec(),
                                                      ttx.getCustomerInfo());
    
    if (TariffTransaction.Type.SIGNUP == txType) {
      // keep track of customer counts
      record.signup(ttx.getCustomerCount());
    }
    else if (TariffTransaction.Type.WITHDRAW == txType) {
      // customers presumably found a better deal
      record.withdraw(ttx.getCustomerCount());
    }
    else if (TariffTransaction.Type.PRODUCE == txType) {
      // if ttx count and subscribe population don't match, it will be hard
      // to estimate per-individual production
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("production by subset " + ttx.getCustomerCount() +
                 " of subscribed population " + record.subscribedPopulation);
      }
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
    }
    else if (TariffTransaction.Type.CONSUME == txType) {
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("consumption by subset " + ttx.getCustomerCount() +
                 " of subscribed population " + record.subscribedPopulation);
      }
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());      
    }
  }

  // --------------- activation -----------------
  /* (non-Javadoc)
   * @see org.powertac.samplebroker.PortfolioManager#activate()
   */
  @Override
  public void activate (int timeslotIndex)
  {
    if (customerSubscriptions.size() == 0) {
      // we have no tariffs
      createInitialTariffs();
    }
    else {
      // we have some, are they good enough?
      improveTariffs();
    }
  }
  
  // Creates initial tariffs for the main power types. These are simple
  // fixed-rate two-part tariffs that give the broker a fixed margin.
  private void createInitialTariffs ()
  {
    // remember that market prices are per mwh, but tariffs are by kwh
    double marketPrice = marketManager.getMeanMarketPrice() / 1000.0;
    // for each power type representing a customer population,
    // create a tariff that's better than what's available
    for (PowerType pt : customerProfiles.keySet()) {
      // we'll just do fixed-rate tariffs for now
      double rateValue;
      if (pt.isConsumption())
        rateValue = ((marketPrice + fixedPerKwh) * (1.0 + defaultMargin));
      else
        rateValue = (-1.0 * marketPrice / (1.0 + defaultMargin));
      if (pt.isInterruptible())
        rateValue *= 0.8; // Magic number!! price break for interruptible
      TariffSpecification spec =
          new TariffSpecification(broker.getBroker(), pt)
              .withPeriodicPayment(defaultPeriodicPayment);
      Rate rate = new Rate().withValue(rateValue);
      if (pt.isInterruptible()) {
        // set max curtailment
        rate.withMaxCurtailment(0.5);
      }
      spec.addRate(rate);
      customerSubscriptions.put(spec, new HashMap<CustomerInfo, CustomerRecord>());
      tariffRepo.addSpecification(spec);
      broker.sendMessage(spec);
    }
  }

  // Checks to see whether our tariffs need fine-tuning
  private void improveTariffs()
  {
    // quick magic-number hack to inject a balancing order
    int timeslotIndex = timeslotRepo.currentTimeslot().getSerialNumber();
    if (371 == timeslotIndex) {
      for (TariffSpecification spec : tariffRepo.findAllTariffSpecifications()) {
        if (PowerType.INTERRUPTIBLE_CONSUMPTION == spec.getPowerType()) {
          BalancingOrder order = new BalancingOrder(broker.getBroker(),
                                                    spec, 
                                                    0.5,
                                                    spec.getRates().get(0).getMinValue() * 0.9);
          broker.sendMessage(order);
        }
      }
    }
  }

  // ------------- test-support methods ----------------
  double getUsageForCustomer (CustomerInfo customer,
                              TariffSpecification tariffSpec,
                              int index)
  {
    CustomerRecord record = getCustomerRecordByTariff(tariffSpec, customer);
    return record.getUsage(index);
  }
  
  // test-support method
  HashMap<PowerType, double[]> getRawUsageForCustomer (CustomerInfo customer)
  {
    HashMap<PowerType, double[]> result = new HashMap<PowerType, double[]>();
    for (PowerType type : customerProfiles.keySet()) {
      CustomerRecord record = customerProfiles.get(type).get(customer);
      if (record != null) {
        result.put(type, record.usage);
      }
    }
    return result;
  }

  // test-support method
  HashMap<String, Integer> getCustomerCounts()
  {
    HashMap<String, Integer> result = new HashMap<String, Integer>();
    for (TariffSpecification spec : customerSubscriptions.keySet()) {
      HashMap<CustomerInfo, CustomerRecord> customerMap = customerSubscriptions.get(spec);
      for (CustomerRecord record : customerMap.values()) {
        result.put(record.customer.getName() + spec.getPowerType(), 
                    record.subscribedPopulation);
      }
    }
    return result;
  }

  //-------------------- Customer-model recording ---------------------
  /**
   * Keeps track of customer status and usage. Usage is stored
   * per-customer-unit, but reported as the product of the per-customer
   * quantity and the subscribed population. This allows the broker to use
   * historical usage data as the subscribed population shifts.
   */
  class CustomerRecord
  {
    CustomerInfo customer;
    int subscribedPopulation = 0;
    double[] usage;
    double alpha = 0.3;
    
    /**
     * Creates an empty record
     */
    CustomerRecord (CustomerInfo customer)
    {
      super();
      this.customer = customer;
      this.usage = new double[broker.getUsageRecordLength()];
    }
    
    CustomerRecord (CustomerRecord oldRecord)
    {
      super();
      this.customer = oldRecord.customer;
      this.usage = Arrays.copyOf(oldRecord.usage, broker.getUsageRecordLength());
    }
    
    // Returns the CustomerInfo for this record
    CustomerInfo getCustomerInfo ()
    {
      return customer;
    }
    
    // Adds new individuals to the count
    void signup (int population)
    {
      subscribedPopulation = Math.min(customer.getPopulation(),
                                      subscribedPopulation + population);
    }
    
    // Removes individuals from the count
    void withdraw (int population)
    {
      subscribedPopulation -= population;
    }
    
    // Customer produces or consumes power. We assume the kwh value is negative
    // for production, positive for consumption
    void produceConsume (double kwh, Instant when)
    {
      int index = getIndex(when);
      produceConsume(kwh, index);
    }
    
    // store profile data at the given index
    void produceConsume (double kwh, int rawIndex)
    {
      int index = getIndex(rawIndex);
      double kwhPerCustomer = kwh / (double)subscribedPopulation;
      double oldUsage = usage[index];
      if (oldUsage == 0.0) {
        // assume this is the first time
        usage[index] = kwhPerCustomer;
      }
      else {
        // exponential smoothing
        usage[index] = alpha * kwhPerCustomer + (1.0 - alpha) * oldUsage;
      }
      log.debug("consume " + kwh + " at " + index +
                ", customer " + customer.getName());
    }
    
    double getUsage (int index)
    {
      if (index < 0) {
        log.warn("usage requested for negative index " + index);
        index = 0;
      }
      return (usage[getIndex(index)] * (double)subscribedPopulation);
    }
    
    // we assume here that timeslot index always matches the number of
    // timeslots that have passed since the beginning of the simulation.
    int getIndex (Instant when)
    {
      int result = (int)((when.getMillis() - timeService.getBase()) /
                         (Competition.currentCompetition().getTimeslotDuration()));
      return result;
    }
    
    private int getIndex (int rawIndex)
    {
      return rawIndex % usage.length;
    }
  }
}
