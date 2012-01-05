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
import java.util.List;

import org.apache.log4j.Logger;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.interfaces.BrokerProxy;
import org.powertac.common.interfaces.CompetitionControl;
import org.powertac.common.interfaces.InitializationService;
import org.powertac.common.repo.RandomSeedRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Initializes and manages some number of server-resident Brokers that are
 * instances of {@link SampleBroker}.
 * @author John Collins
 */
@Service
public class SampleBrokerService
  implements InitializationService
{
  static private Logger log = Logger.getLogger(SampleBrokerService.class);

  @Autowired // routing of outgoing messages
  private BrokerProxy brokerProxyService;
  
  @Autowired // timeslot repository
  private TimeslotRepo timeslotRepo;

  @Autowired // needed to discover sim mode
  private CompetitionControl competitionControlService;

  @Autowired
  private RandomSeedRepo randomSeedRepo;

  private ArrayList<SampleBroker> brokers = new ArrayList<SampleBroker>();

  /**
   * Default constructor, called once when the server starts, before
   * any application-specific initialization has been done.
   */
  public SampleBrokerService ()
  {
    super();
  }

  /**
   * Creates the internal Broker instance that can receive messages intended
   * for local Brokers. It would be a Really Bad Idea to call this at any time
   * other than during the pre-game phase of a competition, because this method
   * does not register the broker in the BrokerRepo, which is a requirement to
   * see the messages.
   */
  public Broker createBroker (String username)
  {
    SampleBroker result = new SampleBroker(username, this); 
    brokers.add(result);
    return result;
  }
  
  // ----------- per-timeslot activation -------------

  @Override
  public void setDefaults ()
  {
    
  }

  @Override
  public String initialize (Competition competition, List<String> completedInits)
  {
    // TODO Auto-generated method stub
    return null;
  }

  public CompetitionControl getCompetitionControlService ()
  {
    return competitionControlService;
  }

  public RandomSeedRepo getRandomSeedRepo ()
  {
    return randomSeedRepo;
  }

  public TimeslotRepo getTimeslotRepo ()
  {
    return timeslotRepo;
  }

  public BrokerProxy getBrokerProxyService ()
  {
    return brokerProxyService;
  }
}
