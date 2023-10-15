/**
 * Copyright (c) 2023 by John Collins.
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

import java.util.PriorityQueue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powertac.common.Broker;
import org.powertac.common.IdGenerator;
import org.powertac.common.TariffSpecification;
import org.powertac.common.Rate;
import org.powertac.common.XMLMessageConverter;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.repo.BrokerRepo;

import com.thoughtworks.xstream.XStream;

class OfferTest
{
  private static BrokerRepo brokerRepo;
  private static Broker jack;

  private Offer uut;
  private XMLMessageConverter converter;

  // set up the broker repo to allow broker value to be initialized in tariffs
  @BeforeAll
  static void initialize ()
  {
    jack = new Broker("Jack");
    brokerRepo = BrokerRepo.getInstance();
    brokerRepo.add(jack);
  }

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp () throws Exception
  {
    IdGenerator.recycle();
    IdGenerator.setPrefix(3);
    uut = new Offer();

    // need this to properly initialize the message converter
    @SuppressWarnings("unused")
    XStream xst = XMLMessageConverter.getXStream();
    converter = new XMLMessageConverter();
    converter.afterPropertiesSet();
  }

  @Test
  void testCreate1 ()
  {
    assertNotNull(uut);
    assertEquals(uut.getTimeslot(), 0);
    assertNull(uut.getTariffSpecification());
  }

  @Test
  void testCreate2 ()
  {
    Offer uut2 = new Offer("a2");
    assertNotNull(uut2);
    //assertEquals("a2", uut2.getName());
    assertEquals(uut.getTimeslot(), 0);
    assertNull(uut.getTariffSpecification());
  }

  @Test
  void testCreate3 ()
  {
    Offer uut3 = new Offer("a3", 333, "<tariff-spec"
            + "  powerType=\"CONSUMPTION\" periodicPayment=\"-0.95\">"
            + "  <broker>Jack</broker>"
            + "  <rates>"
            + "    <rate weeklyBegin=\"-1\" weeklyEnd=\"-1\" dailyBegin=\"-1\" dailyEnd=\"-1\" minValue=\"-0.16115737081256215\"/>"
            + "  </rates>"
            + "</tariff-spec>");
    assertNotNull(uut3);
    assertEquals(uut3.getTimeslot(), 333);
    //assertEquals("a3", uut3.getName()); // getName is a private method
    TariffSpecification ts = uut3.getTariffSpecification();
    assertTrue(ts.getId() > 1000);
    assertTrue(ts.getRates().get(0).getId() > 1000);
    assertNotNull(ts);
  }

  /**
   * Test ordering of multiple offers
   */
  @Test
  void testOfferOrder ()
  {
    PriorityQueue<Offer> offerList = new PriorityQueue<>();
    Offer offer1 = new Offer("a3", 333, "<tariff-spec"
            + "  powerType=\"CONSUMPTION\" periodicPayment=\"-0.95\">"
            + "  <broker>Jack</broker>"
            + "  <rates>"
            + "    <rate weeklyBegin=\"-1\" weeklyEnd=\"-1\" dailyBegin=\"-1\" dailyEnd=\"-1\" minValue=\"-0.16115737081256215\">"
            + "    </rate>"
            + "  </rates>"
            + " </tariff-spec>");
    offerList.add(offer1);
    Offer offer2 = new Offer("a3", 334, "<tariff-spec"
            + "  powerType=\"CONSUMPTION\" periodicPayment=\"-0.95\">"
            + "  <broker>Jack</broker>"
            + "  <rates>"
            + "    <rate weeklyBegin=\"-1\" weeklyEnd=\"-1\" dailyBegin=\"-1\" dailyEnd=\"-1\" minValue=\"-0.16115737081256215\">"
            + "    </rate>"
            + "  </rates>"
            + " </tariff-spec>");
    offerList.add(offer2);
    assertEquals(offer1, offerList.peek());
  }

  /**
   * Test method for {@link org.powertac.samplebroker.Offer#setTimeslot(int)}.
   */
  @Test
  void testSetTimeslot ()
  {
    uut.withTimeslot(42);
    assertEquals(42, uut.getTimeslot());
  }

  /**
   * Test method for {@link org.powertac.samplebroker.Offer#setTariffSpecification(java.lang.String)}.
   */
  @Test
  void testSetTariffSpecificationString ()
  {
    uut.withTimeslot(65);
    assertEquals(65, uut.getTimeslot());
    uut.withTariffSpecXML("<tariff-spec\n"
            + "powerType=\"PRODUCTION\" signupPayment=\"-10.0\" periodicPayment=\"-0.95\">\n"
            + "  <broker>Jack</broker>\n"
            + "  <rates>\n"
            + "    <rate fixed=\"true\" minValue=\"-0.16\" weeklyBegin=\"-1\" weeklyEnd=\"-1\" dailyBegin=\"-1\" dailyEnd=\"-1\">\n"
            + "    </rate>\n"
            + "  </rates>\n"
            + "</tariff-spec>\n");
    TariffSpecification spec = uut.getTariffSpecification();
    assertNotNull(spec);
    assertEquals(PowerType.PRODUCTION, spec.getPowerType());
    assertEquals(jack, spec.getBroker());
    assertEquals(-10.0, spec.getSignupPayment(), 1e-6);
    assertEquals(0.0, spec.getEarlyWithdrawPayment(), 1e-6);
    assertEquals(-0.95, spec.getPeriodicPayment(), 1e-6);
    Rate rate = spec.getRates().get(0);
    assertNotNull(rate);
    assertEquals(-0.16, rate.getMinValue(), 1e-6);
    assertEquals(-1, rate.getWeeklyBegin());
  }

  /**
   * Configure multiple offers
   */
  @Test
  void testMultipleOffers ()
  {
    String config = "samplebroker.offer.instances = t0ev,t0c\n"
            + "samplebroker.offer.t0ev.timeslot = 1\n"
            + "samplebroker.offer.t0ev.tariffSpecification = \"<tariff-spec powerType=\\\"ELECTRIC_VEHICLE\\\" periodicPayment=\\\"-0.95\\\">\"\n"
            + "  + \" <broker>Sample</broker>\"\n"
            + "  + \" <rates>\"\n"
            + "  +   \" <regulation-rate response=\\\"MINUTES\\\" upRegulationPayment=\\\"0.164\\\" downRegulationPayment=\\\"-0.0564\\\"/>\"\n"
            + "  +   \" <rate weeklyBegin=\\\"-1\\\" weeklyEnd=\\\"-1\\\" dailyBegin=\\\"-1\\\" dailyEnd=\\\"-1\\\" minValue=\\\"-0.1128\\\"> </rate>\"\n"
            + "  + \" </rates>\"\n"
            + "  + \" </tariff-spec>\"\n"
            + "samplebroker.offer.t0c.timeslot = 1\n"
            + "samplebroker.offer.t0ev.tariffSpecification = \"<tariff-spec powerType=\\\"CONSUMPTION\\\" periodicPayment=\\\"-0.95\\\">\"\n"
            + "  + \" <broker>Sample</broker>\"\n"
            + "  + \" <rates>\"\n"
            + "  + \"   <rate weeklyBegin=\\\"-1\\\" weeklyEnd=\\\"-1\\\" dailyBegin=\\\"-1\\\" dailyEnd=\\\"-1\\\" minValue=\\\"-0.161\\\">\"\n"
            + "  + \"   </rate>\"\n"
            + "  + \" </rates>\"\n"
            + "  + \" </tariff-spec>\"";
    
  }
}
