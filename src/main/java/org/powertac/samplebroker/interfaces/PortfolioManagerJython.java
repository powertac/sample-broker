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
package org.powertac.samplebroker.interfaces;

import org.apache.log4j.Logger;
import org.powertac.common.TimeService;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TariffRepo;
import org.powertac.common.repo.TimeslotRepo;


/**
 * Interface for portfolio manager, makes usage statistics available.
 * @author John Collins
 */
public interface PortfolioManagerJython extends PortfolioManager
{
  // These are added for the Jython class, duplication from Activatable
  public void init (BrokerContext context, TimeslotRepo timeslotRepo,
                    TariffRepo tariffRepo, CustomerRepo customerRepo,
                    MarketManager marketManager, TimeService timeService,
                    Logger log);

  public void handleMessage (Object message);

  public void activate (int timeslotIndex);
}