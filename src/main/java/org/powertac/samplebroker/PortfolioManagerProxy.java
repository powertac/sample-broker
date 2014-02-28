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

import matlabcontrol.MatlabConnectionException;
import matlabcontrol.MatlabInvocationException;
import matlabcontrol.MatlabProxy;
import matlabcontrol.MatlabProxyFactory;
import org.apache.log4j.Logger;
import org.joda.time.Instant;
import org.powertac.common.*;
import org.powertac.common.msg.*;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.interfaces.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;


/**
 * Handles portfolio-management responsibilities for the broker. This
 * includes composing and offering tariffs, keeping track of customers and their
 * usage, monitoring tariff offerings from competing brokers.
 * <p/>
 * A more complete broker implementation might split this class into two or
 * more classes; the keys are to decide which messages each class handles,
 * what each class does on the activate() method, and what data needs to be
 * managed and shared.
 *
 * @author John Collins
 */
@Service // Spring creates a single instance at startup
public class PortfolioManagerProxy
    implements PortfolioManager, Initializable, Activatable
{
  static private Logger log = Logger.getLogger(PortfolioManagerProxy.class);

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

  private MatlabProxy proxy;

  /**
   * Default constructor registers for messages, must be called after
   * message router is available.
   */
  public PortfolioManagerProxy ()
  {
    super();

    try {
      proxy = new MatlabProxyFactory().getProxy();
      proxy.eval("cd src/main/matlab/portfoliomanager;");
    }
    catch (MatlabConnectionException e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (MatlabInvocationException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Per-game initialization. Configures parameters and registers
   * message handlers.
   */
  @Override // from Initializable
  public void initialize (BrokerContext context)
  {
    propertiesService.configureMe(this);

    try {
      proxy.feval("mInit", context, timeslotRepo, tariffRepo,
          customerRepo, marketManager, timeService, log, null);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  // -------------- data access ------------------
  /**
   * Returns total usage for a given timeslot (represented as a simple index).
   */
  @Override
  public double collectUsage (int index)
  {
    double result = 0.0;
    try {
      Object[] returnArguments = proxy.returningFeval("collectUsage", 1, index);
      result = ((double[]) returnArguments[0])[0];

      if (Double.isNaN(result)) {
        result = 0.0;
      }
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
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
    try {
      proxy.feval("msgCustomerBootstrapData", cbd);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  /**
   * Handles a TariffSpecification. These are sent by the server when new tariffs are
   * published. If it's not ours, then it's a competitor's tariff. We keep track of
   * competing tariffs locally, and we also store them in the tariffRepo.
   */
  public void handleMessage (TariffSpecification spec)
  {
    try {
      proxy.feval("msgTariffSpecification", spec);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  /**
   * Handles a TariffStatus message. This should do something when the status
   * is not SUCCESS.
   */
  public void handleMessage (TariffStatus ts)
  {
    try {
      proxy.feval("msgTariffStatus", ts);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  /**
   * Handles a TariffTransaction. We only care about certain types: PRODUCE,
   * CONSUME, SIGNUP, and WITHDRAW.
   */
  public void handleMessage (TariffTransaction ttx)
  {
    try {
      proxy.feval("msgTariffTransaction", ttx);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  /**
   * Handles a TariffRevoke message from the server, indicating that some
   * tariff has been revoked.
   */
  public void handleMessage (TariffRevoke tr)
  {
    try {
      proxy.feval("msgTariffRevoke", tr);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  /**
   * Handles a BalancingControlEvent, sent when a BalancingOrder is
   * exercised by the DU.
   */
  public void handleMessage (BalancingControlEvent bce)
  {
    try {
      proxy.feval("msgBalancingControlEvent", bce);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }

  // --------------- activation -----------------

  /**
   * Called after TimeslotComplete msg received. Note that activation order
   * among modules is non-deterministic.
   */
  @Override // from Activatable
  public void activate (int timeslotIndex)
  {
    try {
      proxy.feval("activate", timeslotIndex);
    }
    catch (MatlabInvocationException mie) {
      mie.printStackTrace();
    }
  }
}
