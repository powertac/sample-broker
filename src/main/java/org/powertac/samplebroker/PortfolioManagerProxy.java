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

import org.apache.log4j.Logger;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.TimeService;
import org.powertac.common.msg.BalancingControlEvent;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.TariffRevoke;
import org.powertac.common.msg.TariffStatus;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.core.JythonFactory;
import org.powertac.samplebroker.interfaces.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

  private PortfolioManagerJython portfolioManager;

  /**
   * Default constructor registers for messages, must be called after 
   * message router is available.
   */
  public PortfolioManagerProxy ()
  {
    portfolioManager = (PortfolioManagerJython) JythonFactory.getJythonObject(
            "org.powertac.samplebroker.interfaces.PortfolioManager",
            "src/main/jython/PortfolioManagerService.py");
  }

  /**
   * Per-game initialization. Configures parameters and registers
   * message handlers.
   */
  @Override // from Initializable
  public void initialize (BrokerContext context)
  {
    // TODO Check if still needed
    propertiesService.configureMe(this);

    portfolioManager.init(context, timeslotRepo, tariffRepo,
        customerRepo, marketManager, timeService, log);
  }

  @Override
  public double collectUsage (int index)
  {
    return portfolioManager.collectUsage(index);
  }

  // -------------- Message handlers -------------------
  public void handleMessage (CustomerBootstrapData cbd)
  {
    portfolioManager.handleMessage(cbd);
  }

  public void handleMessage (TariffSpecification spec)
  {
    portfolioManager.handleMessage(spec);
  }
  
  public void handleMessage (TariffStatus ts)
  {
    portfolioManager.handleMessage(ts);
  }
  
  public void handleMessage (TariffTransaction ttx)
  {
    portfolioManager.handleMessage(ttx);
  }

  public void handleMessage (TariffRevoke tr)
  {
    portfolioManager.handleMessage(tr);
  }

  public void handleMessage (BalancingControlEvent bce)
  {
    portfolioManager.handleMessage(bce);
  }

  // --------------- activation -----------------
  @Override
  public void activate (int timeslotIndex)
  {
    portfolioManager.activate(timeslotIndex);
  }
}
