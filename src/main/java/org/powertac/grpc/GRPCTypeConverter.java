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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.Instant;
import org.powertac.common.*;
import org.powertac.common.enumerations.PowerType;
import org.powertac.common.msg.BalanceReport;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.MarketBootstrapData;
import org.powertac.common.repo.BrokerRepo;
import org.powertac.common.repo.TimeslotRepo;
import org.powertac.common.xml.BrokerConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Helper class that converts all types forth and back between PB versions and powerTAC originals
 */
@Service
public class GRPCTypeConverter
{

  @Autowired
  BrokerRepo brokerRepo;
  @Autowired
  TimeslotRepo timeslotRepo;
  Logger log = LogManager.getLogger(GRPCTypeConverter.class);

  public GRPCTypeConverter()
  {
  }

  public Timeslot convert(PBTimeslot p)
  {
    return new Timeslot(p.getSerialNumber(), convert(p.getStartInstant()));
  }

  public PBTimeslot convert(Timeslot t)
  {
    return PBTimeslot.newBuilder()
        .setSerialNumber(t.getSerialNumber())
        .setStartInstant(t.getStartInstant().getMillis())
        .build();
  }

  public PBBankTransaction convert(BankTransaction in)
  {
    return PBBankTransaction.newBuilder()
        .setId(in.getId())
        .setAmount(in.getAmount())
        .setPostedTimeslot(in.getPostedTimeslot().getSerialNumber())
        .build();
  }

  public BankTransaction convert(PBBankTransaction pbbtx)
  {
    return new BankTransaction(
        brokerRepo.findByUsername(pbbtx.getBroker()),
        pbbtx.getAmount(),
        pbbtx.getPostedTimeslot());
  }

  public Broker convert(PBBroker pbb)
  {
    return new Broker(pbb.getUsername(), pbb.getLocal(), pbb.getWholesale());
  }

  public PBBroker convert(Broker b)
  {
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

  public Instant convert(long i)
  {
    return new Instant(i);
  }

  public PBCashPosition convert(CashPosition cp)
  {
    return PBCashPosition.newBuilder()
        .setId(cp.getId())
        .setBroker(new BrokerConverter().toString(cp.getBroker()))
        .setPostedTimeslot(convert(cp.getPostedTimeslot()).getSerialNumber())
        .setBalance(cp.getBalance())
        .build();
  }

  public PBDistributionReport convert(DistributionReport dr)
  {
    return PBDistributionReport.newBuilder()
        .setId(dr.getId())
        .setTimeslot(dr.getTimeslot())
        .setTotalConsumption(dr.getTotalConsumption())
        .setTotalProduction(dr.getTotalProduction())
        .build();
  }

  public PBCompetition convert(Competition comp)
  {
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
        .addAllCustomer(convert(comp.getCustomers()))
        .build();
  }

  public PBCustomerInfo convert(CustomerInfo ci)
  {
    return PBCustomerInfo.newBuilder()
        .setId(ci.getId())
        .setName(ci.getName())
        .setPopulation(ci.getPopulation())
        .setPowerType(convert(ci.getPowerType()))
        .setControllableKW(ci.getControllableKW())
        .setCustomerClass(PBCustomerClass.forNumber(ci.getCustomerClass().ordinal()))
        .build();
  }

  public List<PBCustomerInfo> convert(List<CustomerInfo> cil)
  {
    LinkedList<PBCustomerInfo> l = new LinkedList<>();
    for (CustomerInfo c : cil) {
      l.add(convert(c));
    }
    return l;
  }

  public PBPowerType convert(PowerType pt)
  {
    return PBPowerType.newBuilder()
        .setLabel(pt.toString())
        .build();
  }

  public PowerType convert(PBPowerType pbpt)
  {
    return PowerType.valueOf(pbpt.getLabel());
  }

  public PBProperties convert(Properties serverProps)
  {
    Enumeration<?> props = serverProps.propertyNames();
    PBProperties.Builder builder = PBProperties.newBuilder();
    while (props.hasMoreElements()) {
      String key = (String) props.nextElement();
      builder.putValues(key, serverProps.getProperty(key));
    }
    return builder.build();

  }

  public PBMarketBootstrapData convert(MarketBootstrapData in)
  {

    //PBMarketBootstrapData out = basicConversionToPB(PBMarketBootstrapData.class, in, PBMarketBootstrapData.newBuilder());
    //return PBMarketBootstrapData.newBuilder().mergeFrom(out)
    return PBMarketBootstrapData.newBuilder()
        .setId(in.getId())
        .addAllMwh(arrayToList(in.getMwh()))
        .addAllMarketPrice(arrayToList(in.getMarketPrice()))
        .build();
  }

  public PBBalancingTransaction convert(BalancingTransaction tx)
  {
    return PBBalancingTransaction.newBuilder()
        .setId(tx.getId())
        .setKWh(tx.getKWh())
        .setCharge(tx.getCharge())
        .setBroker(tx.getBroker().getUsername())
        .setPostedTimeslot(tx.getPostedTimeslotIndex())
        .build();
  }

  public PBClearedTrade convert(ClearedTrade ct)
  {
    return PBClearedTrade.newBuilder()
        .setId(ct.getId())
        .setDateExecuted(ct.getDateExecuted().getMillis())
        .setExecutionMWh(ct.getExecutionMWh())
        .setExecutionPrice(ct.getExecutionPrice())
        .build();
  }

  public PBDistributionTransaction convert(DistributionTransaction in)
  {
    return PBDistributionTransaction.newBuilder()
        .setId(in.getId())
        .setBroker(in.getBroker().getUsername())
        .setCharge(in.getCharge())
        .setKWh(in.getKWh())
        .setNLarge(in.getNLarge())
        .setNSmall(in.getNSmall())
        .build();
  }

  public PBCapacityTransaction convert(CapacityTransaction in)
  {
    return PBCapacityTransaction.newBuilder()
        .setId(in.getId())
        .setBroker(in.getBroker().getUsername())
        .setCharge(in.getCharge())
        .setKWh(in.getKWh())
        .setPeakTimeslot(in.getPeakTimeslot())
        .setThreshold(in.getThreshold())
        .build();
  }

  public PBMarketPosition convert(MarketPosition in)
  {
    return PBMarketPosition.newBuilder()
        .setId(in.getId())
        .setBroker(in.getBroker().getUsername())
        .setOverallBalance(in.getOverallBalance())
        .setTimeslot(in.getTimeslotIndex())
        .build();

  }

  public PBMarketTransaction convert(MarketTransaction in)
  {
    return PBMarketTransaction.newBuilder()
        .setId(in.getId())
        .setBroker(in.getBroker().getUsername())
        .setMWh(in.getMWh())
        .setPrice(in.getPrice())
        .setTimeslot(in.getTimeslotIndex())
        .build();

  }

  public PBOrderbook convert(Orderbook in)
  {
    return PBOrderbook.newBuilder()
        .setId(in.getId())
        .addAllAsks(convert(in.getAsks()))
        .addAllBids(convert(in.getBids()))
        .setTimeslot(in.getTimeslotIndex())
        .setClearingPrice(in.getClearingPrice())
        .setDateExecuted(in.getDateExecuted().getMillis())
        .build();
  }

  private Iterable<? extends PBOrderbookOrder> convert(SortedSet<OrderbookOrder> asks)
  {
    LinkedList<PBOrderbookOrder> list = new LinkedList<>();
    for (OrderbookOrder ask : asks) {
      list.add(convert(ask));
    }
    return list;
  }

  private PBOrderbookOrder convert(OrderbookOrder in)
  {
    return PBOrderbookOrder.newBuilder()
        .setId(in.getId())
        .setLimitPrice(in.getLimitPrice())
        .setMWh(in.getMWh())
        .build();
  }

  public PBWeatherForecast convert(WeatherForecast in)
  {
    return basicConversionToPB(in, PBWeatherForecast.newBuilder())
        .setId(in.getId())
        .setCurrentTimeslot(in.getTimeslotIndex())
        .addAllPredictions(listConvert(in.getPredictions(), WeatherForecastPrediction.class, PBWeatherForecastPrediction.class))
        .build();

  }

 // private Iterable<? extends PBWeatherForecastPrediction> convert(List<WeatherForecastPrediction> predictions)
 // {
 //   LinkedList<PBWeatherForecastPrediction> list = new LinkedList<>();
 //   for (WeatherForecastPrediction prediction :
 //       predictions) {
 //     PBWeatherForecastPrediction pbPred = basicConversionToPB(prediction, PBWeatherForecastPrediction.newBuilder())
 //         .build();
 //     list.add(pbPred);
 //   }
 //   return list;
 // }

  public PBWeatherReport convert(WeatherReport in)
  {
    return basicConversionToPB(in, PBWeatherReport.newBuilder()).build();
  }

  public PBBalanceReport convert(BalanceReport in)
  {
    return basicConversionToPB(in, PBBalanceReport.newBuilder()).build();
  }

  public PBCustomerBootstrapData convert(CustomerBootstrapData in)
  {
    return basicConversionToPB(in, PBCustomerBootstrapData.newBuilder())
        .addAllNetUsage(arrayToList(in.getNetUsage()))
        .build();
  }

  public PBRate convert(Rate in){
    return basicConversionToPB(in, PBRate.newBuilder())
        .build();
  }

  public PBTariffSpecification convert(TariffSpecification in)
  {

    return basicConversionToPB(in, PBTariffSpecification.newBuilder())
        .setBroker(in.getBroker().getUsername())
        .setExpiration(in.getExpiration().getMillis())
        .setPowerType(convert(in.getPowerType()))
        .addAllRates(listConvert(in.getRates(),Rate.class,  PBRate.class))
        .addAllRegulationRates(listConvert(in.getRegulationRates(), RegulationRate.class, PBRegulationRate.class))
        .addAllSupersedes(in.getSupersedes())
        .build();
  }

  /**
   * Generates a list of
   * @param inputList
   * @param outputClass
   * @param <I>
   * @param <O>
   * @return
   */
  protected <I,O extends GeneratedMessageV3> Iterable<O> listConvert(List<I> inputList, Class<I> inputClass, Class<O> outputClass)
  {
    LinkedList<O> list = new LinkedList<>();
    for (I inputItem :
        inputList) {

      //TODO casting is ugly. Can we avoid it?
      O outputItem = (O) reflectionConvertCall(inputItem, inputClass, outputClass);
      list.add(outputItem);
    }
    return list;
  }


  protected <I, O> O reflectionConvertCall(Object input, Class<I> i, Class<O> o)
  {
    Class[] argClasses = {i};
    Object[] args = {input};
    try {
      Method method = this.getClass().getMethod("convert", argClasses);
      return (O) method.invoke(this, args);
    }
    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      log.error(e);
    }
    return null;
  }


//    public  PBPowerType convert(PowerType pt){
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
  private enum TypeLabel
  {
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

  private List<Double> arrayToList(double[] doubles)
  {
    LinkedList<Double> list = new LinkedList<>();
    for (double aDouble : doubles) {
      list.add(aDouble);
    }
    return list;
  }

  private List<Integer> arrayToList(int[] vals)
  {
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
   * @return
   */
  protected <T extends GeneratedMessageV3.Builder<T>> T basicConversionToPB(Object in, T builder)
  {
    Map<String, String> props = null;
    try {
      props = BeanUtils.describe(in);
    }
    catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      e.printStackTrace();
    }

    for (Map.Entry<String, String> next : props.entrySet()) {
      Descriptors.FieldDescriptor fieldByName = builder.getDescriptorForType().findFieldByName(next.getKey());
      //parsing all different types from string
      if (fieldByName == null) continue;
      try {
        switch (fieldByName.getType()) {

          case DOUBLE:
            builder.setField(fieldByName, Double.parseDouble(next.getValue()));
            break;
          case FLOAT:
            builder.setField(fieldByName, Float.parseFloat(next.getValue()));
            break;
          case INT64:
            builder.setField(fieldByName, Long.parseLong(next.getValue()));
            break;
          case UINT64:
            builder.setField(fieldByName, Long.parseLong(next.getValue()));
            break;
          case INT32:
            builder.setField(fieldByName, Integer.parseInt(next.getValue()));
            break;
          case FIXED64:
            builder.setField(fieldByName, Long.parseLong(next.getValue()));
            break;
          case FIXED32:
            builder.setField(fieldByName, Integer.parseInt(next.getValue()));
            break;
          case BOOL:
            builder.setField(fieldByName, Boolean.parseBoolean(next.getValue()));
            break;
          case STRING:
            builder.setField(fieldByName, next.getValue());
            break;
          case GROUP:
            //TODO is message
            break;
          case MESSAGE:
            //TODO is message
            break;
          case BYTES:
            builder.setField(fieldByName, next.getValue().getBytes());
            break;
          case UINT32:
            builder.setField(fieldByName, Integer.parseInt(next.getValue()));
            break;
          case ENUM:
            //TODO check
            builder.setField(fieldByName, Integer.parseInt(next.getValue()));
            break;
          case SFIXED32:
            builder.setField(fieldByName, Integer.parseInt(next.getValue()));
            break;
          case SFIXED64:
            builder.setField(fieldByName, Long.parseLong(next.getValue()));
            break;
          case SINT32:
            builder.setField(fieldByName, Integer.parseInt(next.getValue()));
            break;
          case SINT64:
            builder.setField(fieldByName, Long.parseLong(next.getValue()));
            break;
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }

    }

    return builder;
  }


  // protected   <T> T basicConversionFromPB(Class<T> type, GeneratedMessageV3 in, T out ) {
  //     Map<Descriptors.FieldDescriptor, Object> fields = in.getAllFields();
  //     for (Map.Entry<Descriptors.FieldDescriptor, Object> next : fields.entrySet()) {
  //         String propertyName = next.getKey().getFullName();
  //         BeanUtils.
  //     }
  // }

  protected <T> T copyProperties(Class<T> outType, GeneratedMessageV3 in, T out)
  {
    try {
      BeanUtils.copyProperties(in, out);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      e.printStackTrace();
    }
    return out;
  }
}

