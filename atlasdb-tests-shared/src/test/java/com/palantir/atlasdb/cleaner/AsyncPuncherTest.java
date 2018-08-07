/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.cleaner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Suppliers;
import com.palantir.common.time.Clock;
import com.palantir.common.time.SystemClock;

public class AsyncPuncherTest {

    private static final long TRANSACTION_TIMEOUT = 10;
    private static final long ASYNC_PUNCHER_INTERVAL = 1;
    private static final long MAX_INTERVALS_TO_WAIT = 100;

    AsyncPuncher asyncPuncher;
    private long counter = 0;

    @Before
    public void setup() {
        PuncherStore puncherStore = InMemoryPuncherStore.create();
        Clock clock = new SystemClock();
        Puncher puncher = SimplePuncher.create(puncherStore, clock, Suppliers.ofInstance(TRANSACTION_TIMEOUT));
        asyncPuncher = AsyncPuncher.create(puncher, ASYNC_PUNCHER_INTERVAL);
    }

    @After
    public void tearDown() {
        asyncPuncher.shutdown();
    }

    @Test
    public void delegatesInitializationCheck() {
        Puncher delegate = mock(Puncher.class);
        Puncher puncher = AsyncPuncher.create(delegate, ASYNC_PUNCHER_INTERVAL);

        when(delegate.isInitialized())
                .thenReturn(false)
                .thenReturn(true);

        assertFalse(puncher.isInitialized());
        assertTrue(puncher.isInitialized());
    }

    @Test
    public void testPuncherDurability() throws Exception {
        Long stored = counter++;
        asyncPuncher.punch(stored);
        Long retrieved = Long.MIN_VALUE;
        for (int i = 0; i < MAX_INTERVALS_TO_WAIT && retrieved < stored; i++) {
            Thread.sleep(ASYNC_PUNCHER_INTERVAL);
            retrieved = asyncPuncher.getTimestampSupplier().get();
        }
        assertEquals(stored, retrieved);
    }

    @Test
    public void testPuncherTimestampLessThanFreshTimestamp() throws Exception {
        Long stored = counter++;
        asyncPuncher.punch(stored);
        Long retrieved = Long.MIN_VALUE;
        for (int i = 0; i < MAX_INTERVALS_TO_WAIT && retrieved < stored; i++) {
            Thread.sleep(ASYNC_PUNCHER_INTERVAL);
            retrieved = asyncPuncher.getTimestampSupplier().get();
        }
        long freshTimestamp = counter++;
        assertTrue(retrieved < freshTimestamp);
    }

}