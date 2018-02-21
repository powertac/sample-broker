
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

import io.grpc.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.powertac.grpc.streams.ContextManagerServiceImpl;
import org.powertac.common.config.ConfigurableValue;
import org.powertac.samplebroker.core.BrokerRunner;
import org.powertac.util.ProxyAuthenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * This class starts the GRPCServer and delegates an interceptor to also start the TAC client once we have a successful
 * GRPC connection from our client.
 */
@Service
public class GRPCServer {

    private static Logger log = LogManager.getLogger();

    @ConfigurableValue(valueType = "Integer", description = "Port to listen to with GRPC server")
    private int port = 3004;

    @Autowired
    GrpcInterceptor interceptor;

    public boolean tacStarted;
    private String[] cliArgs;


    public void startGrpcServer(String[] cliArgs) throws IOException, InterruptedException {
        //storing for later, it's the TAC client that needs this
        this.cliArgs = cliArgs;

        Server server = ServerBuilder.forPort(port)
                .addService(new ContextManagerServiceImpl())
                .intercept(interceptor)
                .build();
        server.start();
        System.out.println("Started GRPC Listener");
        server.awaitTermination();
    }


    private void startPTClient(String[] args) {
        // Check for proxy settings, useSocks=true for brokers
        new ProxyAuthenticator(true);

        BrokerRunner runner = new BrokerRunner();
        runner.processCmdLine(args);

        // if we get here, it's time to exit
        System.exit(0);
    }


    public void startTacClient() {
        this.startPTClient(cliArgs);
        this.tacStarted = true;
    }
}
