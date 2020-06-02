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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powertac.common.Competition;
import org.powertac.common.CustomerInfo;
import org.powertac.common.IdGenerator;
import org.powertac.common.TimeService;
import org.powertac.common.msg.BrokerAccept;
import org.powertac.common.repo.BrokerRepo;
import org.powertac.common.repo.CustomerRepo;
import org.powertac.common.spring.SpringApplicationContext;
import org.powertac.samplebroker.core.BrokerMessageReceiver;
import org.powertac.samplebroker.core.MessageDispatcher;
import org.powertac.samplebroker.core.PowerTacBroker;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test cases for the sample broker implementation.
 * 
 * @author John Collins
 */
public class SampleBrokerTest
{
  private Instant baseTime;

  private PowerTacBroker broker;
  private CustomerRepo customerRepo;
  private BrokerRepo brokerRepo;

  @BeforeEach
  public void setUp () throws Exception
  {
    // set the time
    baseTime = ZonedDateTime.of(2011, 2, 1, 0, 0, 0, 0, TimeService.UTC).toInstant();

    // initialize the broker under test
    broker = new PowerTacBroker();

    // set up the autowired dependencies
    ApplicationContext ctx = mock(ApplicationContext.class);
    BrokerMessageReceiver bmr = mock(BrokerMessageReceiver.class);
    SpringApplicationContext sac = new SpringApplicationContext();
    sac.setApplicationContext(ctx);
    MessageDispatcher messageDispatcher = new MessageDispatcher();
    ReflectionTestUtils.setField(broker, "router", messageDispatcher);
    customerRepo = new CustomerRepo();
    ReflectionTestUtils.setField(broker, "customerRepo", customerRepo);
    TimeService timeService = new TimeService();
    ReflectionTestUtils.setField(broker, "timeService", timeService);
    brokerRepo = new BrokerRepo();
    ReflectionTestUtils.setField(broker, "brokerRepo", brokerRepo);
    ReflectionTestUtils.setField(broker, "brokerMessageReceiver", bmr);
    ReflectionTestUtils.setField(broker, "username", "Sample");

    broker.init();
  }
  
/**
   * Test method for {@link org.powertac.samplebroker.core.PowerTacBroker.BrokerAdapter#isEnabled()}.
   */
  @Test
  public void testIsEnabled ()
  {
    assertFalse(broker.getBroker().isEnabled());
    broker.getBroker().receiveMessage(new BrokerAccept(3));
    assertTrue(broker.getBroker().isEnabled());
    assertEquals(3, IdGenerator.getPrefix(), "correct prefix");
  }

  /**
   * Test method for {@link org.powertac.samplebroker.core.PowerTacBroker.BrokerAdapter#receiveMessage(java.lang.Object)}.
   */
  @Test
  public void testReceiveCompetition ()
  {
    assertEquals(1, broker.getBrokerList().size(), "initially, no competing brokers");
    // set up a competition
    Competition comp = Competition.newInstance("Test")
        .withSimulationBaseTime(baseTime)
        .addBroker("Sam")
        .addBroker("Sally")
        .addCustomer(new CustomerInfo("Podunk", 3))
        .addCustomer(new CustomerInfo("Midvale", 1000))
        .addCustomer(new CustomerInfo("Metro", 100000));
    // send without first enabling
    broker.getBroker().receiveMessage(comp);
    assertEquals(1, broker.getBrokerList().size(), "still no competing brokers");
    // enable the broker
    broker.getBroker().receiveMessage(new BrokerAccept(3));
    // send to broker and check
    broker.getBroker().receiveMessage(comp);
    assertEquals(3, broker.getBrokerList().size(), "2 competing brokers");
    assertEquals(3, customerRepo.size(), "3 customers");
  }
}
