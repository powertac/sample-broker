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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.annotation.Resource;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.log4j.Logger;
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

  // JMS Queue Names
  private String serverQueueName = "serverInput";

  private Map<MessageListener,AbstractMessageListenerContainer> listenerContainerMap = 
      new HashMap<MessageListener,AbstractMessageListenerContainer>();
  
  public String getServerQueueName()
  {
    return serverQueueName;
  }
  
  public void initializeQueues(String brokerQueueName) {
    // create server and broker queues
    createQueue(serverQueueName);
    createQueue(brokerQueueName);
  }
  
  public Queue createQueue(String queueName) {
    Queue queue = null;
    log.info("Creating queue " + queueName);
    try {
      Connection connection = connectionFactory.createConnection();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      queue = session.createQueue(queueName);
    } catch (JMSException e) {
      log.error("Failed to create queue " + queueName, e);
    }
    return queue;
  }
  
  public void registerMessageListener(String destinationName, MessageListener listener) {    
    log.info("registerMessageListener(" + destinationName + ", " + listener + ")");
    DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setDestinationName(destinationName);
    container.setMessageListener(listener);
    container.setTaskExecutor(taskExecutor);
    container.afterPropertiesSet();
    container.start();
    
    listenerContainerMap.put(listener, container);
  }
}
