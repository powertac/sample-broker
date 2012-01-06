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

import org.apache.log4j.Logger;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.Timeslot;
import org.powertac.common.WeatherReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.msg.TimeslotUpdate;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles market interactions on behalf of the broker.
 * @author John Collins
 */
@Service
public class MarketManagerService implements MarketManager
{
  static private Logger log = Logger.getLogger(MarketManagerService.class);
  
  private SampleBroker broker; // master
  
  @Autowired
  private TimeslotRepo timeslotRepo;
  
  @Autowired
  private PortfolioManager portfolioManager;

  // local state
  private double initialConsumptionMargin= 1.1; // customer pays
  private double initialProductionMargin = 0.95;  // broker pays
  
  // max and min offer prices. Max means "sure to trade"
  private double buyLimitPriceMax = -1.0;  // broker pays
  private double buyLimitPriceMin = -100.0;  // broker pays
  private double sellLimitPriceMax = 100.0;    // other broker pays
  private double sellLimitPriceMin = 0.2;    // other broker pays

  // Bid recording
  private HashMap<Timeslot, Order> lastOrder;
  private ArrayList<Timeslot> enabledTimeslots = null;
  private HashMap<Timeslot, ArrayList<MarketTransaction>> marketTxMap;
  private double[] marketMWh;
  private double[] marketPrice;
  private double meanMarketPrice = 0.0;
  private ArrayList<WeatherReport> weather;

  public MarketManagerService ()
  {
    super();
  }
  
  /* (non-Javadoc)
   * @see org.powertac.samplebroker.MarketManager#init(org.powertac.samplebroker.SampleBroker)
   */
  @Override
  public void init (SampleBroker broker)
  {
    this.broker = broker;
    lastOrder = new HashMap<Timeslot, Order>();
    enabledTimeslots = new ArrayList<Timeslot>();
    marketTxMap = new HashMap<Timeslot, ArrayList<MarketTransaction>>();
    weather = new ArrayList<WeatherReport>();
    for (Class<?> messageType: Arrays.asList(BalancingTransaction.class,
                                             ClearedTrade.class,
                                             MarketBootstrapData.class,
                                             MarketPosition.class,
                                             MarketTransaction.class,
                                             TimeslotUpdate.class,
                                             WeatherReport.class)) {
      broker.registerMessageHandler(this, messageType);
    }
  }
  
  // ----------------- data access -------------------
  /**
   * Returns the mean price observed in the market
   */
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
  }

  /**
   * Handles a ClearedTrade message - this is where you would want to keep
   * track of market prices.
   */
  public void handleMessage (ClearedTrade ct)
  {
  }

  /**
   * Receives a MarketBootstrapData message, reporting usage and prices
   * for the bootstrap period. We record the overall weighted mean price,
   * as well as the mean price and usage for a week.
   */
  public void handleMessage (MarketBootstrapData data)
  {
    marketMWh = new double[broker.getUsageRecordLength()];
    marketPrice = new double[broker.getUsageRecordLength()];
    double totalUsage = 0.0;
    double totalValue = 0.0;
    for (int i = 0; i < data.getMwh().length; i++) {
      totalUsage += data.getMwh()[i];
      totalValue += data.getMarketPrice()[i] * data.getMwh()[i];
      if (i < broker.getUsageRecordLength()) {
        // first pass, just copy the data
        marketMWh[i] = data.getMwh()[i];
        marketPrice[i] = data.getMarketPrice()[i];
      }
      else {
        // subsequent passes, accumulate mean values
        int pass = i / broker.getUsageRecordLength();
        int index = i % broker.getUsageRecordLength();
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
    broker.addMarketPosition(posn, posn.getTimeslot());
  }
  
  /**
   * Receives a new MarketTransaction. We look to see whether an order we
   * have placed has cleared.
   */
  public void handleMessage (MarketTransaction tx)
  {
    // reset price escalation when a trade fully clears.
    Order lastTry = lastOrder.get(tx.getTimeslot());
    if (lastTry == null) // should not happen
      log.error("order corresponding to market tx " + tx + " is null");
    else if (tx.getMWh() == lastTry.getMWh()) // fully cleared
      lastOrder.put(tx.getTimeslot(), null);
  }
  
  /**
   * Receives the market orderbooks
   */
  public void handleMessage (Orderbook orderbook)
  {
    
  }
  
  /**
   * Receives a TimeslotUpdate message. 
   */
  public void handleMessage (TimeslotUpdate update)
  {
    enabledTimeslots = new ArrayList<Timeslot>(update.getEnabled());
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
  public void activate ()
  {
    double neededKWh = 0.0;
    for (Timeslot timeslot : timeslotRepo.enabledTimeslots()) {
      int index = (timeslot.getSerialNumber()) % broker.getUsageRecordLength();
      neededKWh = portfolioManager.collectUsage(index);
      submitOrder(neededKWh, timeslot);
    }
  }

  private void submitOrder (double neededKWh, Timeslot timeslot)
  {
    double neededMWh = neededKWh / 1000.0;
    
    Double limitPrice;
    MarketPosition posn = broker.findMarketPositionByTimeslot(timeslot);
    if (posn != null)
      neededMWh -= posn.getOverallBalance();
    log.debug("needed mWh=" + neededMWh);
    if (neededMWh == 0.0) {
      log.info("no power required in timeslot " + timeslot.getSerialNumber());
      return;
    }
    else {
      limitPrice = computeLimitPrice(timeslot, neededMWh);
    }
    log.info("new order for " + neededMWh + " at " + limitPrice +
             " in timeslot " + timeslot.getSerialNumber());
    Order result = new Order(broker, timeslot, neededMWh, limitPrice);
    lastOrder.put(timeslot, result);
    broker.sendMessage(result);
  }

  /**
   * Computes a limit price with a random element. 
   */
  private Double computeLimitPrice (Timeslot timeslot,
                                    double amountNeeded)
  {
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
    if (lastTry != null
        && Math.signum(amountNeeded) == Math.signum(lastTry.getMWh()))
      oldLimitPrice = lastTry.getLimitPrice();

    // set price between oldLimitPrice and maxPrice, according to number of
    // remaining chances we have to get what we need.
    double newLimitPrice = minPrice; // default value
    Timeslot current = timeslotRepo.currentTimeslot();
    int remainingTries = (timeslot.getSerialNumber()
                          - current.getSerialNumber()
                          - Competition.currentCompetition().getDeactivateTimeslotsAhead());
    if (remainingTries > 0) {
      double range = (minPrice - oldLimitPrice) * 2.0 / (double)remainingTries;
      log.debug("oldLimitPrice=" + oldLimitPrice + ", range=" + range);
      double computedPrice = oldLimitPrice + broker.getRandomSeed().nextDouble() * range; 
      return Math.max(newLimitPrice, computedPrice);
    }
    else
      return null; // market order
  }
}
