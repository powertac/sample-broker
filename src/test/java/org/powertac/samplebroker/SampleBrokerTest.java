/*
 * Copyright (c) 2011 by the original author
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.RandomSeed;
import org.powertac.common.interfaces.BrokerProxy;
import org.powertac.common.msg.BrokerAccept;
import org.powertac.common.msg.BrokerAuthentication;
import org.powertac.common.repo.RandomSeedRepo;
import org.powertac.common.repo.TimeslotRepo;

/**
 * Test cases for the sample broker implementation.
 * 
 * @author John Collins
 */
public class SampleBrokerTest
{
  private SampleBrokerService service;
  private BrokerProxy proxy;
  private TimeslotRepo timeslotRepo;
  private RandomSeedRepo randomSeedRepo;
  private RandomSeed randomSeed;
  private Instant baseTime;

  private SampleBroker broker;
  
  @Before
  public void setUp () throws Exception
  {
    // set up mocks
    service = mock(SampleBrokerService.class);
    proxy = mock(BrokerProxy.class);
    timeslotRepo = mock(TimeslotRepo.class);
    randomSeedRepo = mock(RandomSeedRepo.class);
    randomSeed = mock(RandomSeed.class);
    MessageDispatcher messageDispatcher = new MessageDispatcher();
    PortfolioManagerService portfolioManagerService =
        mock(PortfolioManagerService.class);
    MarketManagerService marketManagerService =
        mock(MarketManagerService.class);
    when (service.getBrokerProxyService()).thenReturn(proxy);
    when (service.getTimeslotRepo()).thenReturn(timeslotRepo);
    when (service.getRandomSeedRepo()).thenReturn(randomSeedRepo);
    when (service.getMessageDispatcher()).thenReturn(messageDispatcher);
    when (service.getPortfolioManagerService()).thenReturn(portfolioManagerService);
    when (service.getMarketManagerService()).thenReturn(marketManagerService);
    when (randomSeedRepo.getRandomSeed(anyString(), anyLong(), anyString())).thenReturn(randomSeed);

    // set the time
    baseTime = new DateTime(2011, 2, 1, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();

    // initialize the broker under test
    broker = new SampleBroker("Sample", service);
    broker.init();
  }
  
  /**
   * Test method for {@link org.powertac.samplebroker.SampleBroker#SampleBroker(java.lang.String, org.powertac.samplebroker.SampleBrokerService)}.
   */
  @Test
  public void testSampleBroker ()
  {
    ArgumentCaptor<BrokerAuthentication> login = 
        ArgumentCaptor.forClass(BrokerAuthentication.class);
    verify(proxy).routeMessage(login.capture());
    assertEquals("correct broker", "Sample", login.getValue().getBroker().getUsername());
    verify(randomSeedRepo).getRandomSeed(SampleBroker.class.getName(), 0l, "Sample");
    assertFalse(broker.isEnabled());
  }

  /**
   * Test method for {@link org.powertac.samplebroker.SampleBroker#isEnabled()}.
   */
  @Test
  public void testIsEnabled ()
  {
    assertFalse(broker.isEnabled());
    broker.receiveMessage(new BrokerAccept(3));
    assertTrue(broker.isEnabled());
  }

  /**
   * Test method for {@link org.powertac.samplebroker.SampleBroker#receiveMessage(java.lang.Object)}.
   */
  @Test
  public void testReceiveCompetition ()
  {
    assertEquals("initially, no brokers", 0, broker.getBrokerList().size());
    // set up a competition
    Competition comp = Competition.newInstance("Test")
        .withSimulationBaseTime(baseTime)
        .addBroker("Sam")
        .addBroker("Sally")
        .addCustomer(new CustomerInfo("Podunk", 3))
        .addCustomer(new CustomerInfo("Midvale", 1000))
        .addCustomer(new CustomerInfo("Metro", 100000));
    // send without first enabling
    broker.receiveMessage(comp);
    assertEquals("still no brokers", 0, broker.getBrokerList().size());
    // enable the broker
    broker.receiveMessage(new BrokerAccept(3));
    // send to broker and check
    broker.receiveMessage(comp);
    assertEquals("2 brokers", 2, broker.getBrokerList().size());
    assertEquals("3 customers", 3, broker.getCustomerList().size());
  }
}
