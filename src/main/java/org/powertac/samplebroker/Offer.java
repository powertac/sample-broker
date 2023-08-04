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

import org.powertac.common.TariffSpecification;
import org.powertac.common.XMLMessageConverter;
import org.powertac.common.config.ConfigurableValue;

/**
 * Instance of this class represent tariffs to be offered by the broker at specified
 * times. These 
 */
public class Offer
{
  private String name;
  private int timeslot = 361;
  private TariffSpecification ts = null;
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
    this.ts = spec;
  }

  public Offer (String name, int timeslot, String xmlspec)
  {
    this(name);
    this.timeslot = timeslot;
    this.withTariffSpecification(xmlspec);
  }

  // not sure why anyone would call this method...
  public String getName ()
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
    return ts;
  }

  public Offer withTariffSpecification (TariffSpecification spec)
  {
    ts = spec;
    return this;
  }

  /**
   * Converts xml string to spec instance
   */
  // fluent setter
  @ConfigurableValue (valueType = "String",
          description = "XML string representing a TariffSpecification")
  public void withTariffSpecification (String xmlSpec)
  {
    ts = (TariffSpecification) converter.fromXML(xmlSpec);
  }
}
