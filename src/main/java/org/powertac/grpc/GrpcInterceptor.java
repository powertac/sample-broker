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
import org.joda.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;

public class GrpcInterceptor implements ServerInterceptor {

    private static Logger log = LogManager.getLogger();

    @Autowired
    GRPCServer grpcServer;

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        log.info("GRPC call at: {}", Instant.now());
        ServerCall.Listener<ReqT> listener;

        if (!grpcServer.tacStarted) {
            grpcServer.startTacClient();
            System.out.println("PowerTAC Java Client started");
        }
        try {
            listener = next.startCall(serverCall, metadata);
        } catch (Throwable ex) {
            log.error("Uncaught exception from grpc service");
            serverCall.close(Status.INTERNAL
                    .withCause(ex)
                    .withDescription("Uncaught exception from grpc service"), null);
            return new ServerCall.Listener<ReqT>() {
            };
        }

        return listener;
    }
}
