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

import de.pascalwhoop.powertac.grpc.*;
import org.joda.time.Instant;
import org.powertac.common.*;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.DistributionReport;

import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

/**
 * Helper class that converts all types forth and back between PB versions and powerTAC originals
 */
public class GRPCTypeConverter {
    public static Timeslot timeslotC(PBTimeslot p) {
        return new Timeslot(p.getSerialNumber(), instantC(p.getStartInstant()));
    }

    public static PBTimeslot timeslotC(Timeslot t) {
        return PBTimeslot.newBuilder()
                .setSerialNumber(t.getSerialNumber())
                .setStartInstant(t.getStartInstant().getMillis())
                .build();
    }

    public static PBBankTransaction bankTransactionC(BankTransaction btx) {
        return PBBankTransaction.newBuilder()
                .setId(btx.getId())
                .setAmount(btx.getAmount())
                .setPostedTimeslot(GRPCTypeConverter.timeslotC(btx.getPostedTimeslot()))
                .build();
    }

    public static BankTransaction bankTransactionC(PBBankTransaction pbbtx) {
        return new BankTransaction(
                brokerC(pbbtx.getBroker()),
                pbbtx.getAmount(),
                pbbtx.getPostedTimeslot().getSerialNumber());
    }

    public static Broker brokerC(PBBroker pbb) {
        return new Broker(pbb.getUsername(), pbb.getLocal(), pbb.getWholesale());
    }

    public static PBBroker brokerC(Broker b) {
        return PBBroker.newBuilder()
                .setId(b.getId())
                .setCash(b.getCashBalance())
                .setKey(b.getKey())
                .setUsername(b.getUsername())
                .setPassword(b.getPassword())
                .setIdPrefix(b.getIdPrefix())
                .setWholesale(b.isWholesale())
                .setQueueName(b.toQueueName())
                .setLocal(b.isLocal())
                .setCash(b.getCashBalance())
                //.putAllMktPositions(TODO not able to get those)
                .build();
    }

    public static Instant instantC(long i) {
        return new Instant(i);
    }

    public static PBCashPosition cashPositionC(CashPosition cp) {
        return PBCashPosition.newBuilder()
                .setId(cp.getId())
                .setBroker(brokerC(cp.getBroker()))
                .setPostedTimeslot(timeslotC(cp.getPostedTimeslot()))
                .setBalance(cp.getBalance())
                .build();
    }

    public static PBDistributionReport distributionReportC(DistributionReport dr) {
        return PBDistributionReport.newBuilder()
                .setId(dr.getId())
                .setTimeslot(PBTimeslot.newBuilder()
                        .setSerialNumber(dr.getTimeslot()))
                .setTotalConsumption(dr.getTotalConsumption())
                .setTotalProduction(dr.getTotalProduction())
                .build();
    }

    public static PBCompetition competitionC(Competition comp) {
        return PBCompetition.newBuilder()
                .setId(comp.getId())
                .setName(comp.getName())
                .setDescription(comp.getDescription())
                .setPomId(comp.getPomId())
                .setTimeslotLength(comp.getTimeslotLength())
                .setBootstrapTimeslotCount(comp.getBootstrapTimeslotCount())
                .setBootstrapDiscardedTimeslots(comp.getBootstrapDiscardedTimeslots())
                .setMinimumTimeslotCount(comp.getMinimumTimeslotCount())
                .setExpectedTimeslotCount(comp.getExpectedTimeslotCount())
                .setTimeslotsOpen(comp.getTimeslotsOpen())
                .setDeactivateTimeslotsAhead(comp.getDeactivateTimeslotsAhead())
                .setMinimumOrderQuantity(comp.getMinimumOrderQuantity())
                .setSimulationBaseTime(comp.getSimulationBaseTime().getMillis())
                .setTimezoneOffset(comp.getTimezoneOffset())
                .setLatitude(comp.getLatitude())
                .setSimulationRate(comp.getSimulationRate())
                .setSimulationModulo(comp.getSimulationModulo())
                .addAllBrokers(comp.getBrokers())
                .addAllCustomer(customerInfoC(comp.getCustomers()))
                .build();
    }

    public static PBCustomerInfo customerInfoC(CustomerInfo ci) {
        return PBCustomerInfo.newBuilder()
                .setId(ci.getId())
                .setName(ci.getName())
                .setPopulation(ci.getPopulation())
                .setPowerType(powerTypeC(ci.getPowerType()))
                .setControllableKW(ci.getControllableKW())
                .setCustomerClass(PBCustomerClass.forNumber(ci.getCustomerClass().ordinal()))
                .build();
    }

    public static List<PBCustomerInfo> customerInfoC(List<CustomerInfo> cil) {
        LinkedList<PBCustomerInfo> l = new LinkedList<>();
        for (CustomerInfo c : cil) {
            l.add(customerInfoC(c));
        }
        return l;
    }

    public static PBPowerType powerTypeC(PowerType pt) {
        return PBPowerType.newBuilder()
                .setLabel(pt.toString())
                .build();
    }

    public static PowerType powerTypeC(PBPowerType pbpt) {
        return PowerType.valueOf(pbpt.getLabel());
    }

    public static PBProperties propertiesC(Properties serverProps) {
        Enumeration<?> props = serverProps.propertyNames();
        PBProperties.Builder builder = PBProperties.newBuilder();
        while (props.hasMoreElements()) {
            String key = (String) props.nextElement();
            builder.putValues(key, serverProps.getProperty(key));
        }
        return builder.build();

    }
//    public static PBPowerType powerTypeC(PowerType pt){
//        //TODO using reflection here, dirty trick, there must be a better way to get this info
//        try {
//            Field f = pt.getClass().getDeclaredField("label");
//            f.setAccessible(true);
//            int type = ((TypeLabel)f.get(pt)).ordinal();
//            return PBPowerType.forNumber(type);
//        } catch (NoSuchFieldException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

    //Copy of TypeLabel from private field in PowerType.java. MUST bee same
    private enum TypeLabel {
        CONSUMPTION,
        PRODUCTION,
        STORAGE,
        INTERRUPTIBLE_CONSUMPTION,
        THERMAL_STORAGE_CONSUMPTION,
        SOLAR_PRODUCTION, WIND_PRODUCTION,
        RUN_OF_RIVER_PRODUCTION,
        PUMPED_STORAGE_PRODUCTION,
        CHP_PRODUCTION,
        FOSSIL_PRODUCTION,
        BATTERY_STORAGE,
        ELECTRIC_VEHICLE
    }
}
