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

package com.palantir.atlasdb.timelock.lock.watch;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.palantir.atlasdb.timelock.lock.HeldLocksCollection;
import com.palantir.lock.LockDescriptor;
import com.palantir.lock.watch.LockWatchRequest;
import com.palantir.lock.watch.LockWatchStateUpdate;

public class LockWatchingServiceImpl implements LockWatchingService {
    private final LockEventLog lockEventLog;
    private final HeldLocksCollection heldLocksCollection;
    private final RangeSet<LockDescriptor> ranges = TreeRangeSet.create();

    public LockWatchingServiceImpl(LockEventLog lockEventLog, HeldLocksCollection heldLocksCollection) {
        this.lockEventLog = lockEventLog;
        this.heldLocksCollection = heldLocksCollection;
    }

    @Override
    public void startWatching(LockWatchRequest locksToWatch) {
        RangeSet<LockDescriptor> newRanges = newRangesToWatch(locksToWatch.ranges());
        if (newRanges.isEmpty()) {
            return;
        }
        addToWatches(newRanges);
        logOpenLocks(newRanges);
        logLockWatchEvent(locksToWatch);
    }

    @Override
    public void stopWatching(LockWatchRequest locksToUnwatch) {
        throw new UnsupportedOperationException("Not implemented in this version");
    }

    @Override
    public LockWatchStateUpdate getWatchState(Optional<Long> lastKnownVersion) {
        return lockEventLog.getLogDiff(lastKnownVersion);
    }

    @Override
    public void registerLock(Set<LockDescriptor> locksTakenOut) {
        lockEventLog.logLock(locksTakenOut.stream().filter(this::hasLockWatch));
    }

    @Override
    public void registerUnlock(Set<LockDescriptor> unlocked) {
        lockEventLog.logUnlock(unlocked.stream().filter(this::hasLockWatch));
    }

    private synchronized void addToWatches(RangeSet<LockDescriptor> locksToWatch) {
        ranges.addAll(locksToWatch);
    }

    private void logOpenLocks(RangeSet<LockDescriptor> locksToWatch) {
        lockEventLog.logOpenLocks(heldLocksCollection.locksHeld().filter(locksToWatch::contains));
    }

    private void logLockWatchEvent(LockWatchRequest locksToWatch) {
        lockEventLog.logLockWatchCreated(locksToWatch);
    }

    private synchronized boolean hasLockWatch(LockDescriptor lockDescriptor) {
        return ranges.contains(lockDescriptor);
    }

    private RangeSet<LockDescriptor> newRangesToWatch(Set<Range<LockDescriptor>> rangesToWatch) {
        RangeSet<LockDescriptor> existingComplement = ranges.complement();
        return TreeRangeSet.create(rangesToWatch.stream()
                .map(existingComplement::subRangeSet)
                .map(RangeSet::asRanges)
                .flatMap(Set::stream)
                .collect(Collectors.toList()));
    }
}