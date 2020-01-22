/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.cassandra.multinode;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.atlasdb.keyvalue.api.ClusterAvailabilityStatus;
import com.palantir.atlasdb.keyvalue.cassandra.CassandraKeyValueService;
import org.junit.Test;

public abstract class AbstractNodeAvailabilityTest extends AbstractDegradedClusterTest {

    @Override
    void testSetup(CassandraKeyValueService kvs) {
        // noop
    }

    @Test
    public void nodeAvailabilityStatusShouldBeAsExpected() {
        assertThat(getTestKvs().getClusterAvailabilityStatus()).isEqualTo(expectedNodeAvailabilityStatus());
    }

    protected abstract ClusterAvailabilityStatus expectedNodeAvailabilityStatus();
}
