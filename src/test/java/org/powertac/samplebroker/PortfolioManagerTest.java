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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powertac.common.CustomerInfo;
import org.powertac.common.TimeService;
import org.powertac.common.Timeslot;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.samplebroker.core.BrokerPropertiesService;
import org.powertac.samplebroker.core.PowerTacBroker;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * @author jcollins
 */
public class PortfolioManagerTest
{
  private TimeslotRepo timeslotRepo;
  private CustomerRepo customerRepo;
  
  private PortfolioManagerService portfolioManagerService;
  private PowerTacBroker broker;
  private Instant baseTime;

  /**
   *
   */
  @BeforeEach
  public void setUp () throws Exception
  {
    broker = mock(PowerTacBroker.class);
    timeslotRepo = mock(TimeslotRepo.class);
    customerRepo = new CustomerRepo();
    BrokerPropertiesService bps = mock(BrokerPropertiesService.class);
    when(broker.getUsageRecordLength()).thenReturn(7*24);
    portfolioManagerService = new PortfolioManagerService();
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "timeslotRepo",
                                 timeslotRepo);
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "customerRepo",
                                 customerRepo);
    ReflectionTestUtils.setField(portfolioManagerService,
                                 "propertiesService",
                                 bps);
    portfolioManagerService.initialize(broker);

    // set the time
    baseTime =
        new DateTime(2011, 2, 1, 0, 0, 0, 0, DateTimeZone.UTC).toInstant();
  }
  
  /**
   * Test customer boot data
   */
  @Test
  public void testCustomerBootstrap ()
  {
    // set up a competition
    CustomerInfo podunk = new CustomerInfo("Podunk", 3);
    customerRepo.add(podunk);
    CustomerInfo midvale = new CustomerInfo("Midvale", 1000); 
    customerRepo.add(midvale);
    // create a Timeslot for use by the bootstrap data
    Timeslot ts0 = new Timeslot(8*24, baseTime.plus(TimeService.DAY * 8));
    when(timeslotRepo.currentTimeslot()).thenReturn(ts0);
    // send to broker and check
    double[] podunkData = new double[7*24];
    Arrays.fill(podunkData, 3.6);
    double[] midvaleData = new double[7*24];
    Arrays.fill(midvaleData, 1600.0);
    CustomerBootstrapData boot =
        new CustomerBootstrapData(podunk, PowerType.CONSUMPTION, podunkData);
    portfolioManagerService.handleMessage(boot);
    boot = new CustomerBootstrapData(midvale, PowerType.CONSUMPTION, midvaleData);
    portfolioManagerService.handleMessage(boot);
    double[] podunkUsage = 
        portfolioManagerService.getRawUsageForCustomer(podunk).get(PowerType.CONSUMPTION);
    assertNotNull(podunkUsage, "podunk usage is recorded");
    assertEquals(1.2, podunkUsage[23], 1e-6, "correct usage value for podunk");
    double[] midvaleUsage = 
        portfolioManagerService.getRawUsageForCustomer(midvale).get(PowerType.CONSUMPTION);
    assertNotNull(midvaleUsage, "midvale usage is recorded");
    assertEquals(1.6, midvaleUsage[27], 1e-6, "correct usage value for midvale");
  }
  
  // other tests needed...
}
