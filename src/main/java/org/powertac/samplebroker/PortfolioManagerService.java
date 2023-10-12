/*
 * Copyright (c) 2012-2019 by the original author
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.joda.time.Instant;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.Rate;
import org.powertac.common.RegulationRate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.config.ConfigurableValue;
//import org.powertac.common.config.Configurator;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.BalancingControlEvent;
import org.powertac.common.msg.BalancingOrder;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.EconomicControlEvent;
import org.powertac.common.msg.TariffRevoke;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles portfolio-management responsibilities for the broker. This
 * includes composing and offering tariffs, keeping track of customers and their
 * usage, monitoring tariff offerings from competing brokers.
 * 
 * A more complete broker implementation might split this class into two or
 * more classes; the keys are to decide which messages each class handles,
 * what each class does on the activate() method, and what data needs to be
 * managed and shared.
 * 
 * @author John Collins
 */
@Service // Spring creates a single instance at startup
public class PortfolioManagerService 
implements PortfolioManager, Initializable, Activatable
{
  static Logger log = LogManager.getLogger(PortfolioManagerService.class);
  
  private BrokerContext brokerContext; // master

  // Spring fills in Autowired dependencies through a naming convention
  @Autowired
  private BrokerPropertiesService propertiesService;

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
  private Map<PowerType,
      Map<CustomerInfo, CustomerRecord>> customerProfiles;
  private Map<TariffSpecification,
      Map<CustomerInfo, CustomerRecord>> customerSubscriptions;
  private Map<PowerType, List<TariffSpecification>> competingTariffs;

  // If we are using the hard-coded, somewhat adaptive tariffs, then configurableTariffs
  // should be false
  @ConfigurableValue(valueType = "Boolean",
          description = "if true then tariff offerings are configured")
  private boolean configurableTariffs = false;

  // Keep track of a benchmark price to allow for comparisons between
  // tariff evaluations
  private double benchmarkPrice = 0.0;

  // These customer records need to be notified on activation
  private List<CustomerRecord> notifyOnActivation = new ArrayList<>();

  // Configurable parameters for tariff composition
  // Override defaults in src/main/resources/config/broker.config
  // or in top-level config file
  @ConfigurableValue(valueType = "Double",
          description = "target profit margin")
  private double defaultMargin = 0.5;

  @ConfigurableValue(valueType = "Double",
          description = "Fixed cost/kWh")
  private double fixedPerKwh = -0.06;

  @ConfigurableValue(valueType = "Double",
          description = "Default daily meter charge")
  private double defaultPeriodicPayment = -1.0;
  
  // Here's the list of tariffs to offer
  private PriorityQueue<Offer> offerList;

  /**
   * Default constructor.
   */
  public PortfolioManagerService ()
  {
    super();
  }

  /**
   * Per-game initialization. Registration of message handlers is automated.
   */
  @Override // from Initializable
  public void initialize (BrokerContext context)
  {
    this.brokerContext = context;
    propertiesService.configureMe(this);
    offerList = new PriorityQueue<Offer>();
    customerProfiles = new LinkedHashMap<>();
    customerSubscriptions = new LinkedHashMap<>();
    competingTariffs = new HashMap<>();
    notifyOnActivation.clear();

    // pull in tariff specifications from configuration
    if (configurableTariffs) {
      Collection<?> offerColl = propertiesService.configureInstances(Offer.class);
      //System.out.println("found " + offerColl.size() + " offers");
      if (null != offerColl) {
        for (Object offerObj: offerColl) {
          Offer offer = (Offer) offerObj;
          //System.out.println("Adding " + offer.getName());
          offerList.add(offer);
        }
      }
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
    Map<CustomerInfo, CustomerRecord> customerMap =
        customerProfiles.get(type);
    if (customerMap == null) {
      customerMap = new LinkedHashMap<>();
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
    Map<CustomerInfo, CustomerRecord> customerMap =
        customerSubscriptions.get(spec);
    if (customerMap == null) {
      customerMap = new LinkedHashMap<>();
      customerSubscriptions.put(spec, customerMap);
    }
    CustomerRecord record = customerMap.get(customer);
    if (record == null) {
      // seed with the generic record for this customer
      record =
          new CustomerRecord(getCustomerRecordByPowerType(spec.getPowerType(),
                                                          customer));
      customerMap.put(customer, record);
      // set up deferred activation in case this customer might do regulation
      record.setDeferredActivation();
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
    for (Map<CustomerInfo, CustomerRecord> customerMap : customerSubscriptions.values()) {
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
  public synchronized void handleMessage (CustomerBootstrapData cbd)
  {
    CustomerInfo customer =
            customerRepo.findByNameAndPowerType(cbd.getCustomerName(),
                                                cbd.getPowerType());
    CustomerRecord record = getCustomerRecordByPowerType(cbd.getPowerType(), customer);
    int subs = record.subscribedPopulation;
    record.subscribedPopulation = customer.getPopulation();
    for (int i = 0; i < cbd.getNetUsage().length; i++) {
      record.produceConsume(cbd.getNetUsage()[i], i);
    }
    record.subscribedPopulation = subs;
  }

  /**
   * Handles a TariffSpecification. These are sent by the server when new tariffs are
   * published. If it's not ours, then it's a competitor's tariff. We keep track of 
   * competing tariffs locally, and we also store them in the tariffRepo.
   */
  public synchronized void handleMessage (TariffSpecification spec)
  {
    Broker theBroker = spec.getBroker();
    if (brokerContext.getBrokerUsername().equals(theBroker.getUsername())) {
      if (theBroker != brokerContext.getBroker())
        // strange bug, seems harmless for now
        log.info("Resolution failed for broker " + theBroker.getUsername());
      // if it's ours, just log it, because we already put it in the repo
      TariffSpecification original =
              tariffRepo.findSpecificationById(spec.getId());
      if (null == original)
        log.error("Spec " + spec.getId() + " not in local repo");
      log.info("published " + spec);
    }
    else {
      // otherwise, keep track of competing tariffs, and record in the repo
      addCompetingTariff(spec);
      tariffRepo.addSpecification(spec);
    }
  }

  /**
   * Handles a TariffStatus message. This should do something when the status
   * is not SUCCESS.
   */
  public synchronized void handleMessage (TariffStatus ts)
  {
    log.info("TariffStatus: " + ts.getStatus());
  }

  /**
   * Handles a TariffTransaction. We only care about certain types: PRODUCE,
   * CONSUME, SIGNUP, and WITHDRAW.
   */
  public synchronized void handleMessage(TariffTransaction ttx)
  {
    // make sure we have this tariff
    TariffSpecification newSpec = ttx.getTariffSpec();
    if (newSpec == null) {
      log.error("TariffTransaction type=" + ttx.getTxType()
                + " for unknown spec");
    }
    else {
      TariffSpecification oldSpec =
              tariffRepo.findSpecificationById(newSpec.getId());
      if (oldSpec != newSpec) {
        log.error("Incoming spec " + newSpec.getId() + " not matched in repo");
      }
    }
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
    else if (ttx.isRegulation()) {
      // Regulation transaction -- we record it as production/consumption
      // to avoid distorting the customer record. 
      log.debug("Regulation transaction from {}, {} kWh for {}",
                ttx.getCustomerInfo().getName(),
                ttx.getKWh(), ttx.getCharge());
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
    }
    else if (TariffTransaction.Type.PRODUCE == txType) {
      // if ttx count and subscribe population don't match, it will be hard
      // to estimate per-individual production
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("production by subset {}  of subscribed population {}",
                 ttx.getCustomerCount(), record.subscribedPopulation);
      }
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());
    }
    else if (TariffTransaction.Type.CONSUME == txType) {
      if (ttx.getCustomerCount() != record.subscribedPopulation) {
        log.warn("consumption by subset {} of subscribed population {}",
                 ttx.getCustomerCount(), record.subscribedPopulation);
      }
      record.produceConsume(ttx.getKWh(), ttx.getPostedTime());      
    }
  }

  /**
   * Handles a TariffRevoke message from the server, indicating that some
   * tariff has been revoked.
   */
  public synchronized void handleMessage (TariffRevoke tr)
  {
    Broker source = tr.getBroker();
    log.info("Revoke tariff " + tr.getTariffId()
             + " from " + tr.getBroker().getUsername());
    // if it's from some other broker, we need to remove it from the
    // tariffRepo, and from the competingTariffs list
    if (!(source.getUsername().equals(brokerContext.getBrokerUsername()))) {
      log.info("clear out competing tariff");
      TariffSpecification original =
              tariffRepo.findSpecificationById(tr.getTariffId());
      if (null == original) {
        log.warn("Original tariff " + tr.getTariffId() + " not found");
        return;
      }
      tariffRepo.removeSpecification(original.getId());
      List<TariffSpecification> candidates =
              competingTariffs.get(original.getPowerType());
      if (null == candidates) {
        log.warn("Candidate list is null");
        return;
      }
      candidates.remove(original);
    }
  }

  /**
   * Handles a BalancingControlEvent, sent when a BalancingOrder is
   * exercised by the DU.
   */
  public synchronized void handleMessage (BalancingControlEvent bce)
  {
    log.info("BalancingControlEvent " + bce.getKwh());
  }

  // --------------- activation -----------------
  /**
   * Called after TimeslotComplete msg received. Note that activation order
   * among modules is non-deterministic.
   */
  @Override // from Activatable
  public synchronized void activate (int timeslotIndex)
  {
    Broker me = brokerContext.getBroker();
    if (configurableTariffs) {
      // make offers due in this timeslot
      while (!offerList.isEmpty() && timeslotIndex >= offerList.peek().getTimeslot()) {
        Offer offer = offerList.poll();
        //System.out.println("offering " + offer.getName() + " ts " + timeslotIndex);
        TariffSpecification spec = offer.getTariffSpecification();
        tariffRepo.addSpecification(spec);
        brokerContext.sendMessage(spec);
      }
    }
    else {
      int tariffCount = tariffRepo.findTariffSpecificationsByBroker(me).size();
      if (0 == tariffCount) {
        // we have no tariffs yet
        createInitialTariffs();
      }
      else {
        // we have some, are they good enough?
        improveTariffs();
      }
    }
    for (CustomerRecord record: notifyOnActivation)
      record.activate();
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
      benchmarkPrice = ((marketPrice + fixedPerKwh) * (1.0 + defaultMargin));
      double rateValue = benchmarkPrice;
      double periodicValue = defaultPeriodicPayment;
      if (pt.isProduction()) {
        rateValue = -2.0 * marketPrice;
        periodicValue /= 2.0;
      }
      //if (pt.isStorage()) {
      //  rateValue *= 0.9; // Magic number
      //  periodicValue = 0.0;
      //}
      if (pt.isInterruptible()) {
        rateValue *= 0.7; // Magic number!! price break for interruptible
      }
      //log.info("rateValue = {} for pt {}", rateValue, pt);
      log.info("Tariff {}: rate={}, periodic={}", pt, rateValue, periodicValue);
      TariffSpecification spec =
          new TariffSpecification(brokerContext.getBroker(), pt)
              .withPeriodicPayment(periodicValue);
      Rate rate = new Rate().withValue(rateValue);
      if (pt.isInterruptible() && !pt.isStorage()) {
        // set max curtailment
        rate.withMaxCurtailment(0.4);
      }
      if (pt.isStorage()) {
        // add a RegulationRate
        RegulationRate rr = new RegulationRate();
        rr.withUpRegulationPayment(-rateValue * 1.45)
            .withDownRegulationPayment(rateValue * 0.5); // magic numbers
        spec.addRate(rr);
      }
      spec.addRate(rate);
      customerSubscriptions.put(spec, new LinkedHashMap<>());
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
    }
  }

  // Checks to see whether our tariffs need fine-tuning
  private void improveTariffs()
  {
    System.out.println("improveTariffs");
    // quick magic-number hack to inject a balancing order
    int timeslotIndex = timeslotRepo.currentTimeslot().getSerialNumber();
    if (371 == timeslotIndex) {
      for (TariffSpecification spec :
        tariffRepo.findTariffSpecificationsByBroker(brokerContext.getBroker())) {
        if (PowerType.INTERRUPTIBLE_CONSUMPTION == spec.getPowerType()) {
          BalancingOrder order = new BalancingOrder(brokerContext.getBroker(),
                                                    spec, 
                                                    0.5,
                                                    spec.getRates().get(0).getMinValue() * 0.9);
          brokerContext.sendMessage(order);
        }
      }
      // add a battery storage tariff with overpriced regulation
      // should get few subscriptions...
      TariffSpecification spec = 
              new TariffSpecification(brokerContext.getBroker(),
                                      PowerType.BATTERY_STORAGE);
      Rate rate = new Rate().withValue(benchmarkPrice * 0.9);
      spec.addRate(rate);
      RegulationRate rr = new RegulationRate();
      rr.withUpRegulationPayment(10.0)  // huge payment
      .withDownRegulationPayment(0.00); // free energy
      spec.addRate(rr);
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
      // add a battery storage tariffs slightly better and slightly worse than the original
      spec = new TariffSpecification(brokerContext.getBroker(),
                                     PowerType.BATTERY_STORAGE);
      rate = new Rate().withValue(benchmarkPrice * 0.7);
      spec.addRate(rate);
      rr = new RegulationRate();
      rr.withUpRegulationPayment(-benchmarkPrice * 0.7 * 1.4)
      .withDownRegulationPayment(benchmarkPrice * 0.7 * 0.54); // magic numbers
      spec.addRate(rr);
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);
      //
      spec = new TariffSpecification(brokerContext.getBroker(),
                                     PowerType.BATTERY_STORAGE);
      rate = new Rate().withValue(benchmarkPrice * 0.7);
      spec.addRate(rate);
      rr = new RegulationRate();
      rr.withUpRegulationPayment(-benchmarkPrice * 0.7 * 1.5)
      .withDownRegulationPayment(benchmarkPrice * 0.7 * 0.46); // magic numbers
      spec.addRate(rr);
      tariffRepo.addSpecification(spec);
      brokerContext.sendMessage(spec);

    }
    // magic-number hack to supersede a tariff
    if (380 == timeslotIndex) {
      revokeTariff();
    }
    // magic-number hack to add a TOU tariff
    if (400 == timeslotIndex) {
      addTouTariff();
    }
    // Exercise economic controls every 4 timeslots
    if ((timeslotIndex % 4) == 3) {
      List<TariffSpecification> candidates =
              tariffRepo.findTariffSpecificationsByPowerType(PowerType.INTERRUPTIBLE_CONSUMPTION);
      for (TariffSpecification spec: candidates) {
        if (spec.getBroker() == brokerContext.getBroker()) {
          EconomicControlEvent ece =
                  new EconomicControlEvent(spec, 0.2, timeslotIndex + 1);
          brokerContext.sendMessage(ece);
        }
      }
    }
  }

  // revoke and replace a tariff
  protected void revokeTariff ()
  {
    // find the existing CONSUMPTION tariff
    TariffSpecification oldc = null;
    List<TariffSpecification> candidates =
      tariffRepo.findTariffSpecificationsByBroker(brokerContext.getBroker());
    if (null == candidates || 0 == candidates.size())
      log.error("No tariffs found for broker");
    else {
      // oldc = candidates.get(0);
      for (TariffSpecification candidate: candidates) {
        if (candidate.getPowerType() == PowerType.CONSUMPTION) {
          oldc = candidate;
          break;
        }
      }
      if (null == oldc) {
        log.warn("No CONSUMPTION tariffs found");
      }
      else {
        double rateValue = oldc.getRates().get(0).getValue();
        // create a new CONSUMPTION tariff
        TariffSpecification spec =
          new TariffSpecification(brokerContext.getBroker(),
                                  PowerType.CONSUMPTION)
              .withPeriodicPayment(defaultPeriodicPayment * 1.1);
        Rate rate = new Rate().withValue(rateValue);
        spec.addRate(rate);
        if (null != oldc)
          spec.addSupersedes(oldc.getId());
        //mungId(spec, 6);
        tariffRepo.addSpecification(spec);
        brokerContext.sendMessage(spec);
        // revoke the old one
        TariffRevoke revoke =
          new TariffRevoke(brokerContext.getBroker(), oldc);
        brokerContext.sendMessage(revoke);
      }
    }
  }

  // add a TOU EV tariff
  protected void addTouTariff ()
  {
    TariffSpecification spec = 
            new TariffSpecification(brokerContext.getBroker(),
                                    PowerType.ELECTRIC_VEHICLE);
    // weekday rates
    Rate rate = new Rate().withValue(benchmarkPrice * 0.4)
            .withWeeklyBegin(1)
            .withWeeklyEnd(5)
            .withDailyBegin(0)
            .withDailyEnd(5);
    spec.addRate(rate);
    rate = new Rate().withValue(benchmarkPrice * 0.8)
            .withWeeklyBegin(1)
            .withWeeklyEnd(6)
            .withDailyBegin(6)
            .withDailyEnd(16);
    spec.addRate(rate);
    rate = new Rate().withValue(benchmarkPrice * 1.2)
            .withWeeklyBegin(1)
            .withWeeklyEnd(6)
            .withDailyBegin(17)
            .withDailyEnd(21);
    spec.addRate(rate);
    rate = new Rate().withValue(benchmarkPrice * 0.8)
            .withWeeklyBegin(1)
            .withWeeklyEnd(6)
            .withDailyBegin(22)
            .withDailyEnd(23);
    spec.addRate(rate);
    // weekend rates
    rate = new Rate().withValue(benchmarkPrice * 0.2)
            .withWeeklyBegin(6)
            .withWeeklyEnd(7)
            .withDailyBegin(0)
            .withDailyEnd(6);
    spec.addRate(rate);
    rate = new Rate().withValue(benchmarkPrice * 0.5)
            .withWeeklyBegin(6)
            .withWeeklyEnd(7)
            .withDailyBegin(7)
            .withDailyEnd(23);
    spec.addRate(rate);
    RegulationRate rr = new RegulationRate();
    rr.withUpRegulationPayment(1.0)  // large payment
    .withDownRegulationPayment(0.00); // free energy
    spec.addRate(rr);
    tariffRepo.addSpecification(spec);
    brokerContext.sendMessage(spec);
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
    HashMap<PowerType, double[]> result = new HashMap<>();
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
    HashMap<String, Integer> result = new HashMap<>();
    for (TariffSpecification spec : customerSubscriptions.keySet()) {
      Map<CustomerInfo, CustomerRecord> customerMap = customerSubscriptions.get(spec);
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
    boolean deferredActivation = false;
    double deferredUsage = 0.0;
    int savedIndex = 0;

    /**
     * Creates an empty record
     */
    CustomerRecord (CustomerInfo customer)
    {
      super();
      this.customer = customer;
      this.usage = new double[brokerContext.getUsageRecordLength()];
    }

    CustomerRecord (CustomerRecord oldRecord)
    {
      super();
      this.customer = oldRecord.customer;
      this.usage = Arrays.copyOf(oldRecord.usage, brokerContext.getUsageRecordLength());
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

    // Sets up deferred activation
    void setDeferredActivation ()
    {
      deferredActivation = true;
      notifyOnActivation.add(this);
    }

    // Customer produces or consumes power. We assume the kwh value is negative
    // for production, positive for consumption
    void produceConsume (double kwh, Instant when)
    {
      int index = getIndex(when);
      produceConsume(kwh, index);
    }

    // stores profile data at the given index
    void produceConsume (double kwh, int rawIndex)
    {
      if (deferredActivation) {
        deferredUsage += kwh;
        savedIndex = rawIndex;
      }
      else
        localProduceConsume(kwh, rawIndex);
    }

    // processes deferred recording to accomodate regulation
    void activate ()
    {
      //PortfolioManagerService.log.info("activate {}", customer.getName());
      localProduceConsume(deferredUsage, savedIndex);
      deferredUsage = 0.0;
    }

    private void localProduceConsume (double kwh, int rawIndex)
    {
      int index = getIndex(rawIndex);
      double kwhPerCustomer = 0.0;
      if (subscribedPopulation > 0) {
        kwhPerCustomer = kwh / (double)subscribedPopulation;
      }
      double oldUsage = usage[index];
      if (oldUsage == 0.0) {
        // assume this is the first time
        usage[index] = kwhPerCustomer;
      }
      else {
        // exponential smoothing
        usage[index] = alpha * kwhPerCustomer + (1.0 - alpha) * oldUsage;
      }
      //PortfolioManagerService.log.debug("consume {} at {}, customer {}", kwh, index, customer.getName());
    }

    double getUsage (int index)
    {
      if (index < 0) {
        PortfolioManagerService.log.warn("usage requested for negative index " + index);
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
