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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.powertac.common.BankTransaction;
import org.powertac.common.CashPosition;
import org.powertac.common.Competition;
import org.powertac.common.msg.DistributionReport;
import org.powertac.grpc.GrpcServiceChannel;
import org.powertac.samplebroker.interfaces.BrokerContext;
import org.powertac.samplebroker.interfaces.Initializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handles incoming context and bank messages with example behaviors.
 *
 * @author John Collins
 */
@Service
public class ContextManagerService
    implements Initializable
{
  static private Logger log = LogManager.getLogger(ContextManagerService.class);

  @Autowired
  BrokerContext master;

  @Autowired
  GrpcServiceChannel comm;

  // current cash balance
  private double cash = 0;


  //  @SuppressWarnings("unchecked")
  @Override
  public void initialize(BrokerContext broker)
  {
    master = broker;
// --- no longer needed ---
//    for (Class<?> clazz: Arrays.asList(BankTransaction.class,
//                                       CashPosition.class,
//                                       DistributionReport.class,
//                                       Competition.class,
//                                       java.util.Properties.class)) {
//      broker.registerMessageHandler(this, clazz);
//    }    
  }

  // -------------------- message handlers ---------------------
  //
  // Note that these arrive in JMS threads; If they share data with the
  // agent processing thread, they need to be synchronized.

  /**
   * BankTransaction represents an interest payment. Value is positive for
   * credit, negative for debit.
   */
  public void handleMessage(BankTransaction btx)
  {
    comm.contextStub.handlePBBankTransaction(comm.converter.convert(btx));
  }

  /**
   * CashPosition updates our current bank balance.
   */
  public void handleMessage(CashPosition cp)
  {
    comm.contextStub.handlePBCashPosition(comm.converter.convert(cp));
  }

  /**
   * DistributionReport gives total consumption and production for the timeslot,
   * summed across all brokers.
   */
  public void handleMessage(DistributionReport dr)
  {
    comm.contextStub.handlePBDistributionReport(comm.converter.convert(dr));
  }

  /**
   * Handles the Competition instance that arrives at beginning of game.
   * Here we capture all the customer records so we can keep track of their
   * subscriptions and usage profiles.
   */
  public void handleMessage(Competition comp)
  {
    comm.contextStub.handlePBCompetition(comm.converter.convert(comp));
  }

  /**
   * Receives the server configuration properties.
   */
  public void handleMessage(java.util.Properties serverProps)
  {
    //TODO do I need this?
    //comm.contextStub.handS(comm.converter.convert(dr));
  }
}
