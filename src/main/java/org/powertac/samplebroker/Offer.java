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

//import org.powertac.common.Broker;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.XMLMessageConverter;
import org.powertac.common.config.ConfigurableValue;

/**
 * Instance of this class represent tariffs to be offered by the broker at specified
 * times. These 
 */
public class Offer implements Comparable<Offer>
{
  private String name;
  //private Broker myBroker;
  private int timeslot = 0;
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
    this.withTariffSpecification(xmlspec);
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

  public TariffSpecification getTariffSpecification ()
  {
    return tariffSpecification;
  }

  public Offer withTariffSpecification (TariffSpecification spec)
  {
    tariffSpecification = spec;
    return this;
  }

  /**
   * Converts xml string to spec instance.
   * Note that the conversion does not use the standard constructor, and therefore
   * fails to initialize object ID values. Therefore they must be set here.
   */
  // fluent setter
  @ConfigurableValue (valueType = "String",
          description = "XML string representing a TariffSpecification")
  public void withTariffSpecification (String xmlSpec)
  {
    tariffSpecification = (TariffSpecification) converter.fromXML(xmlSpec);
    for (Rate rate : tariffSpecification.getRates()) {
      rate.setTariffId(tariffSpecification.getId());
      rate.getId();
    }
  }

  // make these things comparable
  @Override
  public int compareTo (Offer other)
  {
    return getTimeslot() - other.getTimeslot();
  }
}
