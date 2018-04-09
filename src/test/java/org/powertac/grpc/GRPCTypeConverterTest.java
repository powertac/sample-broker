/*
 *  Copyright 2009-2018 the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an
 *  "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied. See the License for the specific language
 *  governing permissions and limitations under the License.
 */

package org.powertac.grpc;

import de.pascalwhoop.powertac.grpc.PBBankTransaction;
import de.pascalwhoop.powertac.grpc.PBBroker;
import de.pascalwhoop.powertac.grpc.PBMarketBootstrapData;
import de.pascalwhoop.powertac.grpc.PBTimeslot;
import org.junit.Test;
import org.powertac.common.BankTransaction;
import org.powertac.common.Broker;
import org.powertac.common.Competition;
import org.powertac.common.msg.MarketBootstrapData;

import static org.junit.Assert.assertEquals;

public class GRPCTypeConverterTest {

    GRPCTypeConverter conv = new GRPCTypeConverter();

    @Test
    public void testSimpleConversions() {
        //BeanUtils.copyProperties(null, null);
        MarketBootstrapData in = ValueGenerator.marketBootstrapData;
        PBMarketBootstrapData out = conv.marketBootstrapDataC(in);
        assertEquals(in.getId(), out.getId());
    }

    @Test
    public void testDozerBrokerConversion() {

    }


    @Test
    public void testComplexConversion() {
    }


    //test types
    Broker broker = new Broker("chicken", true, true);
    Competition competition = Competition.newInstance("TestCompetition");

    @Test
    public void timeslotC() {
        Timeslot timeslot = ValueGenerator.timeslot;
        PBTimeslot pbTimeslot = conv.timeslotC(timeslot);
        assertEquals(pbTimeslot.getSerialNumber(), timeslot.getSerialNumber());
        assertEquals(pbTimeslot.getStartInstant(), timeslot.getStartInstant().getMillis());
    }


    @Test
    public void bankTransactionC() {
        BankTransaction in = ValueGenerator.bankTransaction;
        PBBankTransaction out = conv.bankTransactionC(in);
        assertEquals(in.getAmount(), out.getAmount(), 0);
    }

    @Test
    public void bankTransactionC1() {
    }

    @Test
    public void brokerC() {
        Broker in = ValueGenerator.broker;
        PBBroker out = conv.brokerC(in);
        assertEquals(in.getUsername(), out.getUsername());
    }


    @Test
    public void instantC() {
    }

    @Test
    public void cashPositionC() {
    }

    @Test
    public void distributionReportC() {
    }

    @Test
    public void competitionC() {
    }

    @Test
    public void customerInfoC() {
    }

    @Test
    public void customerInfoC1() {
    }

    @Test
    public void powerTypeC() {
    }

    @Test
    public void powerTypeC1() {
    }

    @Test
    public void propertiesC() {
    }

    @Test
    public void marketBootstrapDataC() {
    }

    @Test
    public void basicConversionToPB() {
    }

    @Test
    public void copyProperties() {
    }
}
