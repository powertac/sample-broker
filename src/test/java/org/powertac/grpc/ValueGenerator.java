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
import org.powertac.common.msg.*;

import java.util.*;
import java.util.Properties;

public class ValueGenerator {                                                                                           
                                                                                                                        
    private static int counter = 0;                                                                                     
                                                                                                                        
    public static final String STRING = "String";                                                                       
    public static final int INT = 1;                                                                                    
    public static final float FLOAT = 2f;
    public static final double DOUBLE = 3d;
    public static final long LONG = 4L;
    public static final byte BYTE  = 5;
    public static final short SHORT = 6;
    public static final boolean BOOLEAN = true;                                                                         
    public static final char CHAR = 'c';
    private static final Instant instant = new Instant(8);

    // a bunch of sample objects to use to test the Java <-> PB <-> serialize <-> PB <-> Java loop
    public static BalanceReport              balanceReport                 = new BalanceReport            ( INT, DOUBLE);
    public static Broker                     broker                        = new Broker                   ( STRING, BOOLEAN, BOOLEAN);
    public static TariffSpecification        tariffSpecification           = new TariffSpecification      ( broker, PowerType.CONSUMPTION);
    public static BalancingControlEvent      balancingControlEvent         = new BalancingControlEvent    ( tariffSpecification, DOUBLE, DOUBLE, INT );
    public static BalancingTransaction       balancingTransaction          = new BalancingTransaction     ( broker, INT, DOUBLE, DOUBLE);
    public static BankTransaction            bankTransaction               = new BankTransaction          ( broker, DOUBLE, INT);
    public static CapacityTransaction        capacityTransaction           = new CapacityTransaction      ( broker, INT, INT, DOUBLE, DOUBLE, DOUBLE);
    public static CashPosition               cashPosition                  = new CashPosition             ( broker, DOUBLE, INT);
    public static ClearedTrade               clearedTrade                  = new ClearedTrade             ( INT, DOUBLE, DOUBLE, instant);
    public static Competition                competition                   = Competition.newInstance      ( STRING );
    public static CustomerInfo               customerInfo                  = new CustomerInfo             ( STRING, INT);
    public static CustomerBootstrapData      customerBootstrapData         = new CustomerBootstrapData    ( customerInfo, PowerType.CONSUMPTION, new double[]{DOUBLE, DOUBLE});
    public static DistributionReport         distributionReport            = new DistributionReport       ( INT, DOUBLE, DOUBLE);
    public static DistributionTransaction    distributionTransaction       = new DistributionTransaction  ( broker, INT, INT, INT, DOUBLE, DOUBLE );
    public static MarketBootstrapData        marketBootstrapData           = new MarketBootstrapData      (new double[]{DOUBLE, DOUBLE}, new double[]{DOUBLE, DOUBLE});
    public static MarketPosition             marketPosition                = new MarketPosition           ( broker, INT, DOUBLE);
    public static MarketTransaction          marketTransaction             = new MarketTransaction        ( broker, INT, INT, DOUBLE, DOUBLE);
    public static OrderbookOrder             orderbookOrder                = new OrderbookOrder           ( DOUBLE, DOUBLE);
    public static Orderbook                  orderbook                     = new Orderbook                ( INT, DOUBLE, instant);
    public static Timeslot                   timeslot                      = new Timeslot                 ( INT, instant);
    public static Order                      order                         = new Order                    ( broker, timeslot, DOUBLE, DOUBLE);
    public static PowerType                  powerType                     = PowerType.CONSUMPTION ;
    public static Properties                 properties                    = new Properties               ( );
    public static RateCore                   rateCore                      = new RateCore                 ( );
    public static TariffRevoke               tariffRevoke                  = new TariffRevoke             ( broker, tariffSpecification);
    public static TariffStatus               tariffStatus                  = new TariffStatus             ( broker, LONG, LONG, TariffStatus.Status.success);
    public static TariffTransaction          tariffTransaction             = new TariffTransaction        ( broker, INT, TariffTransaction.Type.CONSUME, tariffSpecification, customerInfo, INT, DOUBLE, DOUBLE, BOOLEAN);
    public static WeatherForecastPrediction  weatherForecastPrediction     = new WeatherForecastPrediction( INT, DOUBLE, DOUBLE, DOUBLE, DOUBLE );
    private static LinkedList<WeatherForecastPrediction> wfpl = new LinkedList<>(Collections.singletonList(weatherForecastPrediction));
    public static WeatherForecast            weatherForecast               = new WeatherForecast          ( INT, wfpl);
    public static WeatherReport              weatherReport                 = new WeatherReport            ( INT, DOUBLE, DOUBLE, DOUBLE, DOUBLE);


}
