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

package com.palantir.lock.client;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Sets;
import com.palantir.lock.v2.LockImmutableTimestampResponse;
import com.palantir.lock.v2.LockRequest;
import com.palantir.lock.v2.LockResponse;
import com.palantir.lock.v2.LockToken;
import com.palantir.lock.v2.StartIdentifiedAtlasDbTransactionResponse;
import com.palantir.lock.v2.TimelockRpcClient;
import com.palantir.lock.v2.TimelockService;
import com.palantir.lock.v2.WaitForLocksRequest;
import com.palantir.lock.v2.WaitForLocksResponse;
import com.palantir.timestamp.TimestampRange;

public final class RemoteTimelockServiceAdapter implements TimelockService {
    private final LockLeaseService lockLeaseService;
    private final TimelockRpcClient timelockRpcClient;
    private final CoalescingTransactionService transactionService;

    private RemoteTimelockServiceAdapter(TimelockRpcClient timelockRpcClient) {
        this.timelockRpcClient = timelockRpcClient;
        this.lockLeaseService = LockLeaseService.create(timelockRpcClient);
        this.transactionService = CoalescingTransactionService.create(lockLeaseService);
    }

    public static TimelockService create(TimelockRpcClient timelockRpcClient) {
        return new RemoteTimelockServiceAdapter(timelockRpcClient);
    }

    @Override
    public long getFreshTimestamp() {
        return timelockRpcClient.getFreshTimestamp();
    }

    @Override
    public TimestampRange getFreshTimestamps(int numTimestampsRequested) {
        return timelockRpcClient.getFreshTimestamps(numTimestampsRequested);
    }

    @Override
    public LockImmutableTimestampResponse lockImmutableTimestamp() {
        return lockLeaseService.lockImmutableTimestamp();
    }

    @Override
    public StartIdentifiedAtlasDbTransactionResponse startIdentifiedAtlasDbTransaction() {
        return transactionService.startIdentifiedAtlasDbTransaction();
    }

    @Override
    public long getImmutableTimestamp() {
        return timelockRpcClient.getImmutableTimestamp();
    }

    @Override
    public LockResponse lock(LockRequest request) {
        return lockLeaseService.lock(request);
    }

    @Override
    public WaitForLocksResponse waitForLocks(WaitForLocksRequest request) {
        return timelockRpcClient.waitForLocks(request);
    }

    @Override
    public Set<LockToken> refreshLockLeases(Set<LockToken> tokens) {
        Set<LockTokenShare> immutableTsTokens = filterImmutableTsTokens(tokens);
        Set<LockToken> lockTokens = filterOutImmutableTsTokens(tokens);


        Set<LockToken> result = lockLeaseService.refreshLockLeases(reduceForRefresh(tokens));

        return Sets.union(
                immutableTsTokens.stream()
                        .filter(t -> result.contains(t.sharedLockToken()))
                        .collect(Collectors.toSet()),
                lockTokens.stream().filter(result::contains).collect(Collectors.toSet()));
    }

    @Override
    public Set<LockToken> unlock(Set<LockToken> tokens) {
        Set<LockTokenShare> immutableTsTokens = filterImmutableTsTokens(tokens);
        Set<LockToken> lockTokens = filterOutImmutableTsTokens(tokens);


        Set<LockToken> result = lockLeaseService.refreshLockLeases(reduceForUnlock(tokens));

        return Sets.union(
                immutableTsTokens.stream()
                        .filter(t -> result.contains(t.sharedLockToken()))
                        .collect(Collectors.toSet()),
                lockTokens.stream().filter(result::contains).collect(Collectors.toSet()));
    }

    @Override
    public long currentTimeMillis() {
        return timelockRpcClient.currentTimeMillis();
    }

    public Set<LockToken> reduceForRefresh(Set<LockToken> tokens) {
        Set<LockTokenShare> immutableTsTokens = filterImmutableTsTokens(tokens);
        Set<LockToken> reducedImmutableTsTokens = immutableTsTokens.stream()
                .map(LockTokenShare::sharedLockToken)
                .collect(Collectors.toSet());

        return Sets.union(filterOutImmutableTsTokens(tokens), reducedImmutableTsTokens);
    }

    public Set<LockToken> reduceForUnlock(Set<LockToken> tokens) {
        Set<LockTokenShare> immutableTsTokens = filterImmutableTsTokens(tokens);
        Set<LockToken> toUnlock = immutableTsTokens.stream()
                .map(LockTokenShare::unlock)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());

        return Sets.union(filterOutImmutableTsTokens(tokens), toUnlock);
    }

    private Set<LockTokenShare> filterImmutableTsTokens(Set<LockToken> tokens) {
        return tokens.stream().filter(t -> t instanceof LockTokenShare)
                .map(t -> (LockTokenShare) t)
                .collect(Collectors.toSet());
    }

    private Set<LockToken> filterOutImmutableTsTokens(Set<LockToken> tokens) {
        return tokens.stream().filter(t -> !(t instanceof LockTokenShare))
                .collect(Collectors.toSet());
    }

}
