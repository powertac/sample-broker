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

import static org.powertac.util.MessageDispatcher.dispatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Service;

/**
 * Routes incoming messages to broker components. 
 * Components must register for specific message types with the broker, 
 * which passes the registrations to this router. For this to work, 
 * registered components must implement a handleMessage(msg) method that
 * takes the specified type as its single argument.
 * @author John Collins
 */
@Service
public class MessageDispatcher
{
  static private Logger log = Logger.getLogger(SampleBroker.class);

  private HashMap<Class<?>, Set<Object>> registrations;
  
  public MessageDispatcher ()
  {
    super();
    registrations = new HashMap<Class<?>, Set<Object>>();
  }
  
  public void registerMessageHandler (Object handler, Class<?> messageType)
  {
    Set<Object> reg = registrations.get(messageType);
    if (reg == null) {
      reg = new HashSet<Object>();
      registrations.put(messageType, reg);
    }
    reg.add(handler);
  }
  
  public void routeMessage (Object message)
  {
    Class<?> clazz = message.getClass();
    //log.info("Route " + clazz.getName());
    Set<Object> targets = registrations.get(clazz);
    if (targets == null) {
      log.warn("no targets for message of type " + clazz.getName());
      return;
    }
    for (Object target: targets) {
      dispatch(target, "handleMessage", message);
    }
  }
  
  // test-support
  Set<Object> getRegistrations (Class<?> messageType)
  {
    return registrations.get(messageType);
  }
}
