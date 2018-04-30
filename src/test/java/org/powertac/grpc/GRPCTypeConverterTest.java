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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.pascalwhoop.powertac.grpc.*;
import org.joda.time.Instant;
import org.junit.Test;
import org.mockito.Mockito;
import org.powertac.common.*;
import org.powertac.common.msg.MarketBootstrapData;
import org.springframework.test.util.ReflectionTestUtils;
import sun.awt.image.ImageWatched;

import java.util.LinkedList;

import static org.junit.Assert.assertEquals;

public class GRPCTypeConverterTest {

    GRPCTypeConverter conv = new GRPCTypeConverter();

    @Test
    public void testSimpleConversions() {
        //BeanUtils.copyProperties(null, null);
        MarketBootstrapData in = ValueGenerator.marketBootstrapData;
        PBMarketBootstrapData out = conv.convert(in);
        assertEquals(in.getId(), out.getId());
    }


    //test types
    Broker broker = new Broker("chicken", true, true);
    Competition competition = Competition.newInstance("TestCompetition");

    @Test
    public void timeslotC() {
        Timeslot timeslot = ValueGenerator.timeslot;
        PBTimeslot pbTimeslot = conv.convert(timeslot);
        assertEquals(pbTimeslot.getSerialNumber(), timeslot.getSerialNumber());
        assertEquals(pbTimeslot.getStartInstant(), timeslot.getStartInstant().getMillis());
    }


    @Test
    public void bankTransactionC() {
        BankTransaction spy = Mockito.spy(ValueGenerator.bankTransaction);
        Mockito.doReturn(new Timeslot(ValueGenerator.INT, new Instant(8))).when(spy).getPostedTimeslot();
        PBBankTransaction out = conv.convert(spy);
        assertEquals(spy.getAmount(), out.getAmount(), 0);
    }

    @Test
    public void bankTransactionC1() {
    }

    @Test
    public void brokerC() {
        Broker in = ValueGenerator.broker;
        in.setKey(ValueGenerator.STRING);
        in.setPassword(ValueGenerator.STRING);
        PBBroker out = conv.convert(in);
        assertEquals(in.getUsername(), out.getUsername());
    }



    @Test
    public void basicConversionToPB() throws JsonProcessingException
    {
        TariffSpecification in = ValueGenerator.tariffSpecification;
        in.withExpiration(Instant.now())
            .addSupersedes(1234);
        PBTariffSpecification out = conv.convert(in);
        assertEquals(in.getSupersedes(), out.getSupersedesList());
    }


    @Test
    public void listConvert() throws JsonProcessingException
    {
        LinkedList<Rate> rates = new LinkedList<>();
        Rate r = new Rate().withDailyBegin(1).withDailyEnd(2);
        ReflectionTestUtils.setField(r, "timeService", new TimeService());
        rates.add(r);
        
        Iterable<PBRate> out = conv.listConvert(rates, Rate.class, PBRate.class);
        for (PBRate pbRate :
            out) {
            assertEquals(1, pbRate.getDailyBegin());
        }

    }

}
