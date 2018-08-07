/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.atlasdb.timelock.hackweek;

import java.nio.ByteBuffer;
import java.util.Optional;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.palantir.atlasdb.protos.generated.TransactionService.TransactionServiceRequest;

public class ProtobufTransactionService {
    private final JamesTransactionService delegate;

    public ProtobufTransactionService(JamesTransactionService delegate) {
        this.delegate = delegate;
    }

    public Optional<ByteBuffer> process(ByteBuffer buffer) {
        try {
            TransactionServiceRequest req = TransactionServiceRequest.parseFrom(buffer);
            return process(req).map(GeneratedMessageV3::toByteString).map(ByteString::asReadOnlyByteBuffer);
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<GeneratedMessageV3> process(TransactionServiceRequest request) {
        switch (request.getType()) {
            case GET_IMMUTABLE_TIMESTAMP: return Optional.of(delegate.getImmutableTimestamp());
            case START_TRANSACTIONS:
                return Optional.of(
                        delegate.startTransactions(request.getStartTransactions().getNumberOfTransactions()));
            case COMMIT_WRITES:
                return Optional.of(delegate.commitWrites(
                        request.getCommitWrites().getStartTimestamp(),
                        request.getCommitWrites().getWritesList()));
            case CHECK_READ_CONFLICTS:
                return Optional.of(delegate.checkReadConflicts(
                        request.getCheckReadConflicts().getStartTimestamp(),
                        request.getCheckReadConflicts().getReadsList(),
                        request.getCheckReadConflicts().getRangeReadsList()));
            case UNLOCK:
                delegate.unlock(request.getUnlock().getStartTimestampsList());
                return Optional.empty();
            default: throw new AssertionError("Unknown message");
        }
    }
}