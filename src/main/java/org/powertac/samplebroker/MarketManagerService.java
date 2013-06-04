/*
 * Copyright (c) 2012-2013 by the original author
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

import org.apache.log4j.Logger;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.DistributionTransaction;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.interfaces.Activatable;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.powertac.samplebroker.interfaces.MarketManager;
import org.powertac.samplebroker.interfaces.PortfolioManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles market interactions on behalf of the master.
 * @author John Collins
 */
@Service
public class MarketManagerService 
implements MarketManager, Initializable, Activatable
{
  static private Logger log = Logger.getLogger(MarketManagerService.class);
  
  private BrokerContext master; // master
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private PortfolioManager portfolioManager;

  // local state
  private Random randomGen = new Random();
  
  // max and min offer prices. Max means "sure to trade"
  private double buyLimitPriceMax = -1.0;  // broker pays
  private double buyLimitPriceMin = -70.0;  // broker pays
  private double sellLimitPriceMax = 70.0;    // other broker pays
  private double sellLimitPriceMin = 0.5;    // other broker pays
  private double minMWh = 0.001; // don't worry about 1 KWh or less

  // Bid recording
  private HashMap<Integer, Order> lastOrder;
  //private HashMap<Integer, ArrayList<MarketTransaction>> marketTxMap;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;
  //private ArrayList<WeatherReport> weather;

  public MarketManagerService ()
  {
    super();
  }

  /* (non-Javadoc)
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.SampleBroker)
   */
  @SuppressWarnings("unchecked")
  @Override
  public void initialize (BrokerContext broker)
  {
    this.master = broker;
    lastOrder = new HashMap<Integer, Order>();
    //marketTxMap = new HashMap<Integer, ArrayList<MarketTransaction>>();
    //weather = new ArrayList<WeatherReport>();
    for (Class<?> messageType: Arrays.asList(BalancingTransaction.class,
                                             ClearedTrade.class,
                                             DistributionTransaction.class,
                                             MarketBootstrapData.class,
                                             MarketPosition.class,
                                             MarketTransaction.class,
                                             Orderbook.class,
                                             WeatherForecast.class,
                                             WeatherReport.class)) {
      broker.registerMessageHandler(this, messageType);
    }
  }
  
  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market
   */
  @Override
  public double getMeanMarketPrice ()
  {
    return meanMarketPrice;
  }
  
  // --------------- message handling -----------------

  /**
   * Handles a BalancingTransaction message.
   */
  public void handleMessage (BalancingTransaction tx)
  {
    log.info("Balancing tx: " + tx.getCharge());
  }

  /**
   * Handles a ClearedTrade message - this is where you would want to keep
   * track of market prices.
   */
  public void handleMessage (ClearedTrade ct)
  {
  }
  
  /**
   * Handles a DistributionTransaction - charges for transporting power
   */
  public void handleMessage (DistributionTransaction dt)
  {
    log.info("Distribution tx: " + dt.getCharge());
  }

  /**
   * Receives a MarketBootstrapData message, reporting usage and prices
   * for the bootstrap period. We record the overall weighted mean price,
   * as well as the mean price and usage for a week.
   */
  public void handleMessage (MarketBootstrapData data)
  {
    marketMWh = new double[master.getUsageRecordLength()];
    marketPrice = new double[master.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;
    for (int i = 0; i < data.getMwh().length; i++) {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < master.getUsageRecordLength()) {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      }
      else {
        // subsequent passes, accumulate mean values
        int pass = i / master.getUsageRecordLength();
        int index = i % master.getUsageRecordLength();
        marketMWh[index] =
            (marketMWh[index] * pass + data.getMwh()[i]) / (pass + 1);
        marketPrice[index] =
            (marketPrice[index] * pass + data.getMarketPrice()[i]) / (pass + 1);
      }
    }
    meanMarketPrice = totalValue / totalUsage;
  }

  /**
   * Receives a MarketPosition message, representing our commitments on 
   * the wholesale market
   */
  public void handleMessage (MarketPosition posn)
  {
    master.getBroker().addMarketPosition(posn, posn.getTimeslotIndex());
  }
  
  /**
   * Receives a new MarketTransaction. We look to see whether an order we
   * have placed has cleared.
   */
  public void handleMessage (MarketTransaction tx)
  {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslotIndex());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslotIndex(), null);
  }
  
  /**
   * Receives the market orderbooks
   */
  public void handleMessage (Orderbook orderbook)
  {
    // implement something here.
  }
  
  /**
   * Receives a new WeatherForecast.
   */
  public void handleMessage (WeatherForecast forecast)
  {
  }

  /**
   * Receives a new WeatherReport.
   */
  public void handleMessage (WeatherReport report)
  {
  }

  // ----------- per-timeslot activation ---------------
  
  // Finally, once we have a full week of records, we use the data for
  // the hour and day-of-week.
  /* (non-Javadoc)
   * @see org.powertac.samplebroker.MarketManager#activate()
   */
  @Override
  public void activate (int timeslotIndex)
  {
    double neededKWh = 0.0;
    log.debug("Current timeslot is " + timeslotRepo.currentTimeslot().getSerialNumber());
    for (Timeslot timeslot : timeslotRepo.enabledTimeslots()) {
      int index = (timeslot.getSerialNumber()) % master.getUsageRecordLength();
      neededKWh = portfolioManager.collectUsage(index);
      submitOrder(neededKWh, timeslot.getSerialNumber());
    }
  }

  private void submitOrder (double neededKWh, int timeslot)
  {
    double neededMWh = neededKWh / 1000.0;
    
    MarketPosition posn =
        master.getBroker().findMarketPositionByTimeslot(timeslot);
    if (posn != null)
      neededMWh -= posn.getOverallBalance();
    log.debug("needed mWh=" + neededMWh +
              ", timeslot " + timeslot);
    if (Math.abs(neededMWh) <= minMWh) {
      log.info("no power required in timeslot " + timeslot);
      return;
    }
    Double limitPrice = computeLimitPrice(timeslot, neededMWh);
    log.info("new order for " + neededMWh + " at " + limitPrice +
             " in timeslot " + timeslot);
    Order order = new Order(master.getBroker(), timeslot, neededMWh, limitPrice);
    lastOrder.put(timeslot, order);
    master.sendMessage(order);
  }

  /**
   * Computes a limit price with a random element. 
   */
  private Double computeLimitPrice (int timeslot,
                                    double amountNeeded)
  {
    log.debug("Compute limit for " + amountNeeded + 
              ", timeslot " + timeslot);
    // start with default limits
    Double oldLimitPrice;
    double minPrice;
    if (amountNeeded > 0.0) {
      // buying
      oldLimitPrice = buyLimitPriceMax;
      minPrice = buyLimitPriceMin;
    }
    else {
      // selling
      oldLimitPrice = sellLimitPriceMax;
      minPrice = sellLimitPriceMin;
    }
    // check for escalation
    Order lastTry = lastOrder.get(timeslot);
    if (lastTry != null)
      log.debug("lastTry: " + lastTry.getMWh() +
                " at " + lastTry.getLimitPrice());
    if (lastTry != null
        && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh())) {
      oldLimitPrice = lastTry.getLimitPrice();
      log.debug("old limit price: " + oldLimitPrice);
    }

    // set price between oldLimitPrice and maxPrice, according to number of
    // remaining chances we have to get what we need.
    double newLimitPrice = minPrice; // default value
    int current = timeslotRepo.currentSerialNumber();
    int remainingTries = (timeslot - current
                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    log.debug("remainingTries: " + remainingTries);
    if (remainingTries > 0) {
      double range = (minPrice - oldLimitPrice) * 2.0 / (double)remainingTries;
      log.debug("oldLimitPrice=" + oldLimitPrice + ", range=" + range);
      double computedPrice = oldLimitPrice + randomGen.nextDouble() * range; 
      return Math.max(newLimitPrice, computedPrice);
    }
    else
      return null; // market order
  }
}
