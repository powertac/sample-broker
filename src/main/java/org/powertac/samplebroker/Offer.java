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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//import org.powertac.common.Broker;
//import org.powertac.common.Rate;
import org.powertac.common.RateCore;
import org.powertac.common.TariffSpecification;
import org.powertac.common.XMLMessageConverter;
import org.powertac.common.config.ConfigurableValue;
//import org.powertac.common.config.Configurator;

/**
 * Instance of this class represent tariffs to be offered by the broker at specified
 * times. These 
 */
public class Offer implements Comparable<Offer>
{
  static private Logger log = LogManager.getLogger(Offer.class);

  private String name;
  //private Broker myBroker;
  private int timeslot = 0;
  private String xml;
  private TariffSpecification tariffSpecification = null;
  XMLMessageConverter converter;

  public Offer ()
  {
    super();
    converter = new XMLMessageConverter();
    converter.afterPropertiesSet();
  }

  public Offer (String name)
  {
    this();
    this.name = name;
    log.debug("Created Offer {}", name);
  }

  public Offer (String name, int timeslot, TariffSpecification spec)
  {
    this(name);
    this.timeslot = timeslot;
    this.tariffSpecification = spec;
  }

  public Offer (String name, int timeslot, String xmlspec)
  {
    this(name);
    this.timeslot = timeslot;
    this.withTariffSpecXML(xmlspec);
  }

  // not sure why anyone would call this method -- the name property is needed
  // by the configureInstances() method.
  String getName ()
  {
    return name;
  }

  public int getTimeslot ()
  {
    return timeslot;
  }

  // fluent setter
  @ConfigurableValue (valueType = "Integer",
          description = "timesot in which this offer is to be made")
  public Offer withTimeslot (int slot)
  {
    timeslot = slot;
    return this;
  }

  /**
   * We must ensure that the object ID values are set.
   * @return
   */
  public TariffSpecification getTariffSpecification ()
  {
    if (null == tariffSpecification) {
      if (null == xml) {
        log.error("Offer {} null xml string, ts {}", getName(), timeslot);
        return null;
      }
      // This where we turn the xml into a tariff spec
      tariffSpecification = (TariffSpecification) converter.fromXML(xml);
      // Now we need to set the ID values
      long id;
      for (RateCore rate : tariffSpecification.getRates()) {
        id = rate.getId();
        rate.setTariffId(tariffSpecification.getId());
        log.info("Offer {} TariffSpecification {}, Rate {}",
                 getName(), tariffSpecification.getId(), id);
      }
      for (RateCore rate : tariffSpecification.getRegulationRates()) {
        id = rate.getId();
        log.info("Offer {} RegulationRate {}", getName(), id);
        rate.setTariffId(tariffSpecification.getId());
      }
    }
    return tariffSpecification;
  }

  //@ConfigurableValue (valueType = "TariffSpecification",
  //        description = "tariff spec for this offer")
  public Offer withTariffSpecification (TariffSpecification spec)
  {
    tariffSpecification = spec;
    return this;
  }

  /**
   * Dummy getter for the XML setter
   */
  public String getTariffSpecXML ()
  {
    return xml;
  }

  /**
   * Stores xml string. It will be converted to a spec instance as needed, in
   * getTariffSpecification().
   */
  // fluent setter
  @ConfigurableValue (valueType = "String",
          description = "XML string representing a TariffSpecification")
  public void withTariffSpecXML (String xmlSpec)
  {
    xml = xmlSpec;
    //System.out.println("tariff spec " + xmlSpec);
    log.info("offer {} withTariffSpecXML({})", getName(), xmlSpec);
  }

  // make these things comparable
  @Override
  public int compareTo (Offer other)
  {
    return getTimeslot() - other.getTimeslot();
  }
}
