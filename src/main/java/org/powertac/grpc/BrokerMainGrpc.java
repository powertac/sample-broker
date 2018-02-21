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

import org.powertac.grpc.GRPCServer;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;

/**
 * alternative main class for Grpc based client that waits for a GRPC client first before connecting to the server. This
 * ensures the messages received from the server actually end up with the client we want them to end up with.
 */
public class BrokerMainGrpc {

    private static ClassPathXmlApplicationContext context;

    public static void main(String[] args) {

        // initialize and run
        if (null == context) {
            context = new ClassPathXmlApplicationContext("broker.xml");
        }
        //get hold of bean context
        GRPCServer grpcServer = (GRPCServer) context.getBeansOfType(GRPCServer.class).values().toArray()[0];

        try {
            System.out.println("Starting Client");
            grpcServer.startGrpcServer(args);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
