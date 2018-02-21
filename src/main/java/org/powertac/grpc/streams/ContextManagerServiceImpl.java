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

package org.powertac.grpc.streams;

import com.google.protobuf.RpcUtil;
import de.pascalwhoop.powertac.grpc.*;
import io.grpc.stub.StreamObserver;
import org.powertac.common.BankTransaction;
import org.powertac.common.CashPosition;
import org.powertac.common.Competition;
import org.powertac.common.msg.DistributionReport;
import org.powertac.grpc.GRPCTypeConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.bind.annotation.XmlAccessorOrder;

/**
 * GRPC message handler for ContextManagerService. This only works with one client so if a second client connects to the
 * server, the stream gets replaced with the new client and the first client doesn't receive any messages anymore nor
 * does he receive a closed message.
 */
@Service
public class ContextManagerServiceImpl extends ContextManagerServiceGrpc.ContextManagerServiceImplBase {


    private StreamObserver<PBBankTransaction> pbBankTransactionStreamObserver;
    private StreamObserver<PBCashPosition> pbCashPositionStreamObserver;
    private StreamObserver<PBDistributionReport> pbDistributionReportStreamObserver;
    private StreamObserver<PBCompetition> pbCompetitionStreamObserver;
    private StreamObserver<PBProperties> pbPropertiesStreamObserver;

    @Autowired
    GRPCTypeConverter converter;

    @Override
    public void handlePBProperties(PBProperties request, StreamObserver<PBProperties> responseObserver) {
        if (pbPropertiesStreamObserver != null) {
            throw new RpcUtil.AlreadyCalledException();
        }
        pbPropertiesStreamObserver = responseObserver;
    }

    @Override
    public void handlePBBankTransaction(PBRequestStream request, StreamObserver<PBBankTransaction> responseObserver) {
        if (pbBankTransactionStreamObserver != null) {
            throw new RpcUtil.AlreadyCalledException();
        }
        pbBankTransactionStreamObserver = responseObserver;
    }

    @Override
    public void handlePBCashPosition(PBRequestStream request, StreamObserver<PBCashPosition> responseObserver) {
        if (pbCashPositionStreamObserver != null) {
            throw new RpcUtil.AlreadyCalledException();
        }
        pbCashPositionStreamObserver = responseObserver;
    }

    @Override
    public void handlePBDistributionReport(PBRequestStream request, StreamObserver<PBDistributionReport> responseObserver) {
        if (pbDistributionReportStreamObserver != null) {
            throw new RpcUtil.AlreadyCalledException();
        }
        pbDistributionReportStreamObserver = responseObserver;
    }

    @Override
    public void handlePBCompetition(PBRequestStream request, StreamObserver<PBCompetition> responseObserver) {
        if (pbCompetitionStreamObserver != null) {
            throw new RpcUtil.AlreadyCalledException();
        }
        pbCompetitionStreamObserver = responseObserver;
    }


    //
    // Note that these arrive in JMS threads; If they share data with the
    // agent processing thread, they need to be synchronized.

    /**
     * BankTransaction represents an interest payment. Value is positive for credit, negative for debit.
     */
    public void handleMessage(BankTransaction btx) {
        PBBankTransaction pbbtx = converter.bankTransactionC(btx);
        pbBankTransactionStreamObserver.onNext(pbbtx);
    }

    /**
     * CashPosition updates our current bank balance.
     */
    public void handleMessage(CashPosition cp) {
        PBCashPosition pbcp = converter.cashPositionC(cp);
        pbCashPositionStreamObserver.onNext(pbcp);
    }

    /**
     * DistributionReport gives total consumption and production for the timeslot, summed across all brokers.
     */
    public void handleMessage(DistributionReport dr) {
        PBDistributionReport pbdr = converter.distributionReportC(dr);
        pbDistributionReportStreamObserver.onNext(pbdr);
    }

    /**
     * Handles the Competition instance that arrives at beginning of game. Here we capture all the customer records so
     * we can keep track of their subscriptions and usage profiles.
     */
    public void handleMessage(Competition comp) {
        PBCompetition pbc = converter.competitionC(comp);
        pbCompetitionStreamObserver.onNext(pbc);
    }

    /**
     * Receives the server configuration properties.
     */
    public void handleMessage(java.util.Properties serverProps) {
        PBProperties pbp = converter.propertiesC(serverProps);
        pbPropertiesStreamObserver.onNext(pbp);
    }

    /**
     * closes all streams, client needs to reopen connection.
     */
    public void closeAll() {
        pbPropertiesStreamObserver.onCompleted();
        pbPropertiesStreamObserver = null;
        pbCompetitionStreamObserver.onCompleted();
        pbCompetitionStreamObserver = null;
        pbDistributionReportStreamObserver.onCompleted();
        pbDistributionReportStreamObserver = null;
        pbBankTransactionStreamObserver.onCompleted();
        pbBankTransactionStreamObserver = null;
        pbCashPositionStreamObserver.onCompleted();
        pbCashPositionStreamObserver = null;
    }
}
