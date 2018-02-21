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

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import de.pascalwhoop.powertac.grpc.*;
import org.apache.commons.beanutils.BeanUtils;
import org.dozer.DozerBeanMapperBuilder;
import org.dozer.DozerInitializer;
import org.dozer.Mapper;
import org.joda.time.Instant;
import org.powertac.common.*;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.grpc.conversion.InstantCustomConverter;
import org.powertac.grpc.conversion.InstantFactory;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Helper class that converts all types forth and back between PB versions and powerTAC originals
 */
@Service
public class GRPCTypeConverter {

    private Mapper mapper;


    public GRPCTypeConverter() {
        mapper = DozerBeanMapperBuilder
                .create()
                .withCustomConverter(new InstantCustomConverter())
                .withBeanFactory("InstantFactory" ,new InstantFactory())
                .withMappingFiles("dozer-mappings.xml")
                .build();
    }

//    public Timeslot timeslotC(PBTimeslot p) {
//        return mapper.map(p, Timeslot.class);
//        //return new Timeslot(p.getSerialNumber(), instantC(p.getStartInstant()));
//    }

    public PBTimeslot timeslotC(Timeslot t) {
        return mapper.map(t, PBTimeslot.class);
//        return PBTimeslot.newBuilder()
//                .setSerialNumber(t.getSerialNumber())
//                .setStartInstant(t.getStartInstant().getMillis())
//                .build();
    }

    public PBBankTransaction bankTransactionC(BankTransaction btx) {

        return mapper.map(btx, PBBankTransaction.class);
//        return PBBankTransaction.newBuilder()
//                .setId(btx.getId())
//                .setAmount(btx.getAmount())
//                .setPostedTimeslot(timeslotC(btx.getPostedTimeslot()))
//                .build();
    }

//    public BankTransaction bankTransactionC(PBBankTransaction pbbtx) {
//        return new BankTransaction(
//                brokerC(pbbtx.getBroker()),
//                pbbtx.getAmount(),
//                pbbtx.getPostedTimeslot().getSerialNumber());
//    }

//    public Broker brokerC(PBBroker pbb) {
//        return new Broker(pbb.getUsername(), pbb.getLocal(), pbb.getWholesale());
//    }

    public PBBroker brokerC(Broker b) {
        return mapper.map(b, PBBroker.class);

//        return PBBroker.newBuilder()
//                .setId(b.getId())
//                .setCash(b.getCashBalance())
//                .setKey(b.getKey())
//                .setUsername(b.getUsername())
//                .setPassword(b.getPassword())
//                .setIdPrefix(b.getIdPrefix())
//                .setWholesale(b.isWholesale())
//                .setQueueName(b.toQueueName())
//                .setLocal(b.isLocal())
//                .setCash(b.getCashBalance())
//                //.putAllMktPositions(TODO not able to get those)
//                .build();
    }

    public Instant instantC(long i) {
        return new Instant(i);
    }

    public PBCashPosition cashPositionC(CashPosition cp) {
        return PBCashPosition.newBuilder()
                .setId(cp.getId())
                .setBroker(brokerC(cp.getBroker()))
                .setPostedTimeslot(timeslotC(cp.getPostedTimeslot()))
                .setBalance(cp.getBalance())
                .build();
    }

    public PBDistributionReport distributionReportC(DistributionReport dr) {
        return PBDistributionReport.newBuilder()
                .setId(dr.getId())
                .setTimeslot(PBTimeslot.newBuilder()
                        .setSerialNumber(dr.getTimeslot()))
                .setTotalConsumption(dr.getTotalConsumption())
                .setTotalProduction(dr.getTotalProduction())
                .build();
    }

    public PBCompetition competitionC(Competition comp) {
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

    public PBCustomerInfo customerInfoC(CustomerInfo ci) {
        return PBCustomerInfo.newBuilder()
                .setId(ci.getId())
                .setName(ci.getName())
                .setPopulation(ci.getPopulation())
                .setPowerType(powerTypeC(ci.getPowerType()))
                .setControllableKW(ci.getControllableKW())
                .setCustomerClass(PBCustomerClass.forNumber(ci.getCustomerClass().ordinal()))
                .build();
    }

    public List<PBCustomerInfo> customerInfoC(List<CustomerInfo> cil) {
        LinkedList<PBCustomerInfo> l = new LinkedList<>();
        for (CustomerInfo c : cil) {
            l.add(customerInfoC(c));
        }
        return l;
    }

    public PBPowerType powerTypeC(PowerType pt) {
        return PBPowerType.newBuilder()
                .setLabel(pt.toString())
                .build();
    }

    public PowerType powerTypeC(PBPowerType pbpt) {
        return PowerType.valueOf(pbpt.getLabel());
    }

    public PBProperties propertiesC(Properties serverProps) {
        Enumeration<?> props = serverProps.propertyNames();
        PBProperties.Builder builder = PBProperties.newBuilder();
        while (props.hasMoreElements()) {
            String key = (String) props.nextElement();
            builder.putValues(key, serverProps.getProperty(key));
        }
        return builder.build();

    }

    public PBMarketBootstrapData marketBootstrapDataC(MarketBootstrapData in) {
        PBMarketBootstrapData out = basicConversionToPB(PBMarketBootstrapData.class, in, PBMarketBootstrapData.newBuilder());
        return PBMarketBootstrapData.newBuilder().mergeFrom(out)
                .addAllMwh(convertArrToList(in.getMwh()))
                .addAllMarketPrice(convertArrToList(in.getMarketPrice()))
                .build();
    }
//    public  PBPowerType powerTypeC(PowerType pt){
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

    private List<Double> convertArrToList(double[] doubles) {
        LinkedList<Double> list = new LinkedList<>();
        for (double aDouble : doubles) {
            list.add(aDouble);
        }
        return list;
    }

    private List<Integer> convertArrToList(int[] vals) {
        LinkedList<Integer> list = new LinkedList<>();
        for (int v : vals) {
            list.add(v);
        }
        return list;
    }

    /**
     * provides a base conversion helper that converts any basic types of an object into that of a PB version Complete
     * the object conversion afterwards, as this only covers the basics
     *
     * @param type
     * @param in
     * @param out
     * @param <T>
     * @return
     */
    protected <T> T basicConversionToPB(Class<T> type, Object in, GeneratedMessageV3.Builder<PBMarketBootstrapData.Builder> out) {
        Map<String, String> props = null;
        try {
            props = BeanUtils.describe(in);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            e.printStackTrace();
        }

        for (Map.Entry<String, String> next : props.entrySet()) {
            Descriptors.FieldDescriptor fieldByName = out.getDescriptorForType().findFieldByName(next.getKey());
            //parsing all different types from string
            if (fieldByName == null) continue;
            try {
                switch (fieldByName.getType()) {

                    case DOUBLE:
                        out.setField(fieldByName, Double.parseDouble(next.getValue()));
                        break;
                    case FLOAT:
                        out.setField(fieldByName, Float.parseFloat(next.getValue()));
                        break;
                    case INT64:
                        out.setField(fieldByName, Long.parseLong(next.getValue()));
                        break;
                    case UINT64:
                        out.setField(fieldByName, Long.parseLong(next.getValue()));
                        break;
                    case INT32:
                        out.setField(fieldByName, Integer.parseInt(next.getValue()));
                        break;
                    case FIXED64:
                        out.setField(fieldByName, Long.parseLong(next.getValue()));
                        break;
                    case FIXED32:
                        out.setField(fieldByName, Integer.parseInt(next.getValue()));
                        break;
                    case BOOL:
                        out.setField(fieldByName, Boolean.parseBoolean(next.getValue()));
                        break;
                    case STRING:
                        out.setField(fieldByName, next.getValue());
                        break;
                    case GROUP:
                        //TODO is message
                        break;
                    case MESSAGE:
                        //TODO is message
                        break;
                    case BYTES:
                        out.setField(fieldByName, next.getValue().getBytes());
                        break;
                    case UINT32:
                        out.setField(fieldByName, Integer.parseInt(next.getValue()));
                        break;
                    case ENUM:
                        //TODO check
                        out.setField(fieldByName, Integer.parseInt(next.getValue()));
                        break;
                    case SFIXED32:
                        out.setField(fieldByName, Integer.parseInt(next.getValue()));
                        break;
                    case SFIXED64:
                        out.setField(fieldByName, Long.parseLong(next.getValue()));
                        break;
                    case SINT32:
                        out.setField(fieldByName, Integer.parseInt(next.getValue()));
                        break;
                    case SINT64:
                        out.setField(fieldByName, Long.parseLong(next.getValue()));
                        break;
                }
            } catch (ClassCastException e) {
                //e.printStackTrace();
            }

        }

        T result = (T) out.build();

        return (T) out.build();
    }


    // protected   <T> T basicConversionFromPB(Class<T> type, GeneratedMessageV3 in, T out ) {
    //     Map<Descriptors.FieldDescriptor, Object> fields = in.getAllFields();
    //     for (Map.Entry<Descriptors.FieldDescriptor, Object> next : fields.entrySet()) {
    //         String propertyName = next.getKey().getFullName();
    //         BeanUtils.
    //     }
    // }

    protected <T> T copyProperties(Class<T> outType, GeneratedMessageV3 in, T out) {
        try {
            BeanUtils.copyProperties(in, out);
        } catch (IllegalAccessException | InvocationTargetException e) {
            //e.printStackTrace();
        }
        return out;
    }
}

