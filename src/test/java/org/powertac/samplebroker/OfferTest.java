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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.powertac.common.Broker;
import org.powertac.common.TariffSpecification;
import org.powertac.common.XMLMessageConverter;
import org.powertac.common.enumerations.PowerType;

import com.thoughtworks.xstream.XStream;

class OfferTest
{
  Offer uut;
  XMLMessageConverter converter;
  Broker jack = new Broker("Jack");

  /**
   * @throws java.lang.Exception
   */
  @BeforeEach
  void setUp () throws Exception
  {
    uut = new Offer();
    XStream xst = XMLMessageConverter.getXStream();
    converter = new XMLMessageConverter();
    converter.afterPropertiesSet();
  }

  @Test
  void testCreate1 ()
  {
    assertNotNull(uut);
    assertEquals(uut.getTimeslot(), 361);
    assertNull(uut.getTariffSpecification());
  }

  @Test
  void testCreate2 ()
  {
    Offer uut2 = new Offer("a2");
    assertNotNull(uut2);
    assertEquals("a2", uut2.getName());
    assertEquals(uut.getTimeslot(), 361);
    assertNull(uut.getTariffSpecification());
  }

  @Test
  void testCreate3 ()
  {
    Offer uut3 = new Offer("a3", 333, "<tariff-spec\n"
            + "id=\"31\" expiration=\"0\" minDuration=\"0\" powerType=\"CONSUMPTION\" signupPayment=\"0.0\" earlyWithdrawPayment=\"0.0\" periodicPayment=\"-0.95\">\n"
            + "  <broker>Jack</broker>\n"
            + "  <rates>\n"
            + "    <rate id=\"32\" tariffId=\"31\" weeklyBegin=\"-1\" weeklyEnd=\"-1\" dailyBegin=\"-1\" dailyEnd=\"-1\" tierThreshold=\"0.0\" fixed=\"true\" minValue=\"-0.16115737081256215\" maxValue=\"0.0\" noticeInterval=\"0\" expectedMean=\"0.0\" maxCurtailment=\"0.0\">\n"
            + "    </rate>\n"
            + "  </rates>\n"
            + "</tariff-spec>\n");
    assertNotNull(uut3);
    assertEquals(uut3.getTimeslot(), 333);
    assertEquals("a3", uut3.getName());
    assertNotNull(uut3.getTariffSpecification());
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
   * Test method for {@link org.powertac.samplebroker.Offer#setTariffSpecification(org.powertac.common.TariffSpecification)}.
   */
  @Test
  void testSetTariffSpecificationTariffSpecification ()
  {
    
  }

  /**
   * Test method for {@link org.powertac.samplebroker.Offer#setTariffSpecification(java.lang.String)}.
   */
  @Test
  void testSetTariffSpecificationString ()
  {
    uut.withTimeslot(65);
    assertEquals(65, uut.getTimeslot());
    uut.withTariffSpecification("<tariff-spec\n"
            + "id=\"41\" expiration=\"0\" minDuration=\"0\" powerType=\"PRODUCTION\" signupPayment=\"0.0\" earlyWithdrawPayment=\"0.0\" periodicPayment=\"-0.95\">\n"
            + "  <broker>Jack</broker>\n"
            + "  <rates>\n"
            + "    <rate id=\"42\" tariffId=\"41\" weeklyBegin=\"-1\" weeklyEnd=\"-1\" dailyBegin=\"-1\" dailyEnd=\"-1\" tierThreshold=\"0.0\" fixed=\"true\" minValue=\"-0.16115737081256215\" maxValue=\"0.0\" noticeInterval=\"0\" expectedMean=\"0.0\" maxCurtailment=\"0.0\">\n"
            + "    </rate>\n"
            + "  </rates>\n"
            + "</tariff-spec>\n");
    TariffSpecification spec = uut.getTariffSpecification();
    assertNotNull(spec);
    assertEquals(41, spec.getId());
    assertEquals(PowerType.PRODUCTION, spec.getPowerType());
  }
}
