/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.atlasdb.timelock.paxos;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.immutables.value.Value;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.palantir.atlasdb.factory.ServiceCreator;
import com.palantir.atlasdb.http.AtlasDbHttpClients;
import com.palantir.atlasdb.util.MetricsManager;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import com.palantir.conjure.java.config.ssl.TrustContext;
import com.palantir.paxos.PaxosAcceptor;
import com.palantir.paxos.PaxosAcceptorNetworkClient;
import com.palantir.paxos.PaxosLearner;
import com.palantir.paxos.PaxosLearnerNetworkClient;
import com.palantir.paxos.SingleLeaderAcceptorNetworkClient;
import com.palantir.paxos.SingleLeaderLearnerNetworkClient;
import com.palantir.timelock.config.TimeLockInstallConfiguration;
import com.palantir.timelock.paxos.ClientAwarePaxosAcceptor;
import com.palantir.timelock.paxos.ClientAwarePaxosAcceptorAdapter;
import com.palantir.timelock.paxos.ClientAwarePaxosLearner;
import com.palantir.timelock.paxos.ClientAwarePaxosLearnerAdapter;
import com.palantir.timelock.paxos.PaxosRemotingUtils;

public class ClientPaxosResourceFactory {

    private ClientPaxosResourceFactory() { }

    public static ClientResources create(
            MetricsManager metrics,
            Path logDirectory,
            TimeLockInstallConfiguration install,
            ExecutorService sharedExecutor) {
        PaxosComponents paxosComponents = new PaxosComponents(metrics.getTaggedRegistry(), "bound-store", logDirectory);
        PaxosResource paxosResource = new PaxosResource(paxosComponents);
        BatchPaxosAcceptor acceptorResource = new BatchPaxosAcceptorResource(paxosComponents, new AcceptorCacheImpl());
        BatchPaxosLearner learnerResource = new BatchPaxosLearnerResource(paxosComponents);
        BatchPaxosResource batchPaxosResource = new BatchPaxosResource(acceptorResource, learnerResource);

        int quorumSize = PaxosRemotingUtils.getQuorumSize(PaxosRemotingUtils.getClusterAddresses(install));

        NetworkClientFactories factories;
        if (install.paxos().clientPaxos().useBatchPaxos()) {
            factories = batch(metrics.getRegistry(), install, quorumSize, sharedExecutor, batchPaxosResource);
        } else {
            factories = singleLeader(metrics.getRegistry(), install, quorumSize, sharedExecutor, paxosComponents);
        }

        return ImmutableClientResources.builder()
                .quorumSize(quorumSize)
                .components(paxosComponents)
                .nonBatchedResource(paxosResource)
                .batchedResource(batchPaxosResource)
                .networkClientFactories(factories)
                .addAllCloseables(factories.closeables())
                .build();
    }

    @Value.Immutable
    public interface ClientResources {
        int quorumSize();
        PaxosComponents components();
        PaxosResource nonBatchedResource();
        BatchPaxosResource batchedResource();
        NetworkClientFactories networkClientFactories();
        List<Closeable> closeables();
    }

    public interface Factory<T> {
        T create(Client client);
    }

    @Value.Immutable
    public interface NetworkClientFactories {
        Factory<PaxosAcceptorNetworkClient> acceptor();
        Factory<PaxosLearnerNetworkClient> learner();
        List<Closeable> closeables();
    }

    private static NetworkClientFactories singleLeader(
            MetricRegistry metrics,
            TimeLockInstallConfiguration install,
            int quorumSize,
            ExecutorService sharedExecutor,
            PaxosComponents components) {
        List<ClientAwarePaxosAcceptor> remoteClientAwareAcceptors = createProxies(
                ClientAwarePaxosAcceptor.class,
                "timestamp-bound-store.acceptor",
                metrics,
                install);

        Factory<PaxosAcceptorNetworkClient> acceptorClientFactory = client -> {
            List<PaxosAcceptor> remoteAcceptors =
                    ClientAwarePaxosAcceptorAdapter.wrap(remoteClientAwareAcceptors).apply(client);
            PaxosAcceptor localAcceptor = components.acceptor(client);
            List<PaxosAcceptor> allAcceptors = instrument(metrics, PaxosAcceptor.class, localAcceptor, remoteAcceptors);
            return new SingleLeaderAcceptorNetworkClient(
                    allAcceptors,
                    quorumSize,
                    Maps.asMap(ImmutableSet.copyOf(allAcceptors), $ -> sharedExecutor));
        };

        List<ClientAwarePaxosLearner> remoteClientAwareLearners = createProxies(
                ClientAwarePaxosLearner.class,
                "timestamp-bound-store.learner",
                metrics,
                install);

        Factory<PaxosLearnerNetworkClient> learnerClientFactory = client -> {
            List<PaxosLearner> remoteLearners =
                    ClientAwarePaxosLearnerAdapter.wrap(remoteClientAwareLearners).apply(client);
            PaxosLearner localLearner = components.learner(client);
            List<PaxosLearner> allLearners = instrument(metrics, PaxosLearner.class, localLearner, remoteLearners);
            return new SingleLeaderLearnerNetworkClient(
                    localLearner,
                    allLearners,
                    quorumSize,
                    Maps.asMap(ImmutableSet.copyOf(allLearners), $ -> sharedExecutor));
        };

        return ImmutableNetworkClientFactories.builder()
                .acceptor(acceptorClientFactory)
                .learner(learnerClientFactory)
                .build();
    }

    private static NetworkClientFactories batch(
            MetricRegistry metricRegistry,
            TimeLockInstallConfiguration install,
            int quorumSize,
            ExecutorService sharedExecutor,
            BatchPaxosResource resource) {
        List<BatchPaxosAcceptor> remoteBatchAcceptors = createProxies(
                BatchPaxosAcceptor.class,
                "timestamp-bound-store.batch-acceptor",
                metricRegistry,
                install);

        List<BatchPaxosAcceptor> allBatchAcceptors =
                instrument(metricRegistry, BatchPaxosAcceptor.class, resource.acceptor(), remoteBatchAcceptors);

        AutobatchingPaxosAcceptorNetworkClientFactory acceptorFactory =
                AutobatchingPaxosAcceptorNetworkClientFactory.create(allBatchAcceptors, sharedExecutor, quorumSize);

        List<BatchPaxosLearner> remoteBatchLearners = createProxies(
                BatchPaxosLearner.class,
                "timestamp-bound-store.batch-learner",
                metricRegistry,
                install);

        List<BatchPaxosLearner> allBatchLearners =
                instrument(metricRegistry, BatchPaxosLearner.class, resource.learner(), remoteBatchLearners);

        AutobatchingPaxosLearnerNetworkClientFactory learnerFactory =
                AutobatchingPaxosLearnerNetworkClientFactory.create(allBatchLearners, sharedExecutor, quorumSize);

        return ImmutableNetworkClientFactories.builder()
                .acceptor(acceptorFactory::paxosAcceptorForClient)
                .learner(learnerFactory::paxosLearnerForClient)
                .addCloseables(acceptorFactory, learnerFactory)
                .build();
    }

    private static <T> List<T> createProxies(
            Class<T> clazz,
            String userAgent,
            MetricRegistry metricRegistry,
            TimeLockInstallConfiguration install) {
        Set<String> remoteUris = PaxosRemotingUtils.getRemoteServerPaths(install);
        Optional<TrustContext> trustContext = PaxosRemotingUtils.getSslConfigurationOptional(install)
                .map(SslSocketFactories::createTrustContext);
        return remoteUris.stream()
                .map(uri -> AtlasDbHttpClients.createProxy(
                        metricRegistry,
                        trustContext,
                        uri,
                        clazz,
                        userAgent,
                        false))
                .collect(Collectors.toList());
    }

    private static <T> List<T> instrument(MetricRegistry metricRegistry, Class<T> clazz, T local, List<T> remotes) {
        return Stream.concat(Stream.of(local), remotes.stream())
                .map(p -> ServiceCreator.createInstrumentedService(metricRegistry, p, clazz))
                .collect(Collectors.toList());
    }
}