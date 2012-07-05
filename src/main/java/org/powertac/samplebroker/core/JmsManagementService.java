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
package org.powertac.samplebroker.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.pool.PooledConnectionFactory;
import org.apache.log4j.Logger;
import org.powertac.common.config.ConfigurableValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Service;

/**
 * 
 * @author Nguyen Nguyen, John Collins
 */
@Service
public class JmsManagementService {
  static private Logger log = Logger.getLogger(JmsManagementService.class);

  @Resource(name="jmsFactory")
  private ConnectionFactory connectionFactory;
  
  @Autowired
  private Executor taskExecutor;
  
  @Autowired
  private BrokerPropertiesService brokerPropertiesService;
  
  // configurable parameters
  private String serverQueueName = "serverInput"; 
  private String jmsBrokerUrl = "tcp://localhost:61616";
  
  // JMS artifacts
  Connection connection;
  boolean connectionOpen = false;
  DefaultMessageListenerContainer container;
  Session session;

  private Map<MessageListener,AbstractMessageListenerContainer> listenerContainerMap = 
      new HashMap<MessageListener,AbstractMessageListenerContainer>();
  
  public void init (String overridenBrokerUrl, String destinationName)
  {
    brokerPropertiesService.configureMe(this);
    if (overridenBrokerUrl != null && !overridenBrokerUrl.isEmpty()) {
      setJmsBrokerUrl(overridenBrokerUrl);
    }
    
    if (connectionFactory instanceof PooledConnectionFactory) {
      PooledConnectionFactory pooledConnectionFactory = (PooledConnectionFactory) connectionFactory;
      if (pooledConnectionFactory.getConnectionFactory() instanceof ActiveMQConnectionFactory) {
        ActiveMQConnectionFactory amqConnectionFactory = (ActiveMQConnectionFactory) pooledConnectionFactory
                .getConnectionFactory();
        amqConnectionFactory.setBrokerURL(getJmsBrokerUrl());
      }
    }
    
    // create the queue first
    boolean success = false;
    while (!success) {
      try {
        createQueue(destinationName);
        success = true;
      }
      catch (JMSException e) {
        log.info("JMS message broker not ready - delay and retry");
        try {
          Thread.sleep(2000);
        }
        catch (InterruptedException e1) {
          // ignore exception
        }
      }
    }
  }
  
  public void registerMessageListener(MessageListener listener,
                                      String destinationName)
  {
    log.info("registerMessageListener(" + destinationName + ", " + listener + ")");
    container = new DefaultMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setDestinationName(destinationName);
    container.setMessageListener(listener);
    container.setTaskExecutor(taskExecutor);
    container.afterPropertiesSet();
    container.start();
    
    listenerContainerMap.put(listener, container);
  }

  public void createQueue (String queueName) throws JMSException
  {
    // now we can create the queue
    connection = connectionFactory.createConnection();
    connectionOpen = true;
    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    session.createQueue(queueName);
    session.close();
    log.info("JMS Queue " + queueName + " created");
  }

  public synchronized void shutdown ()
  {
    Runnable callback = new Runnable() {
      @Override
      public void run ()
      {
        closeConnection();
      }
    };
    container.stop(callback);
    
    while (connectionOpen) {
      try {
        wait();
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  private synchronized void closeConnection ()
  {
    try {
      //session.close();
      connection.close();
      connectionOpen = false;
      notifyAll();
    }
    catch (JMSException e) {
      e.printStackTrace();
    }
  }
  
  public String getServerQueueName()
  {
    return serverQueueName;
  }
  /**
   * @param serverQueueName the serverQueueName to set
   */
  public void setServerQueueName (String serverQueueName)
  {
    this.serverQueueName = serverQueueName;
  }  
  /**
   * @return the jmsBrokerUrl
   */
  public String getJmsBrokerUrl ()
  {
    return jmsBrokerUrl;
  }

  /**
   * @param jmsBrokerUrl the jmsBrokerUrl to set
   */
  @ConfigurableValue(valueType = "String",
          description = "JMS broker URL to use")  
  public void setJmsBrokerUrl (String jmsBrokerUrl)
  {
    this.jmsBrokerUrl = jmsBrokerUrl;
  }  
}
