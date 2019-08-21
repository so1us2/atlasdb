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
package com.palantir.atlasdb.http;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.google.common.collect.ImmutableList;
<<<<<<< HEAD
=======
import com.palantir.conjure.java.config.ssl.TrustContext;
>>>>>>> 36ed254... Switch to user-agents

import okhttp3.CipherSuite;
import okhttp3.ConnectionSpec;
import okhttp3.TlsVersion;

public final class FeignOkHttpClients {
    private static final int CONNECTION_POOL_SIZE = 100;
    private static final long KEEP_ALIVE_TIME_MILLIS = TimeUnit.MILLISECONDS.convert(10, TimeUnit.MINUTES);

    /**
     * @deprecated Do not use; this method may be removed at any time. It is purely for internal benchmarking, which
     * adds additional settings to the http clients.
     */
    @Deprecated
    public static volatile Consumer<okhttp3.OkHttpClient.Builder> globalClientSettings = (client) -> { };

    public static final ImmutableList<ConnectionSpec> CONNECTION_SPEC_WITH_CYPHER_SUITES = ImmutableList.of(
            new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .cipherSuites(
                            // This GCM cipher suite is for HTTP/2 over TLS1.2 as clients have to
                            // enable at least one cipher suite not in the blacklist.
                            // (https://http2.github.io/http2-spec/index.html#BadCipherSuites)
                            // Timelock server will support HTTP/2 connections, and this will ensure
                            // all AtlasDB clients have one supported cipher suite.
                            // See also:
                            //    - https://http2.github.io/http2-spec/index.html#rfc.section.9.2.2
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                            // In an ideal world, we'd use GCM suites, but they're an order of
                            // magnitude slower than the CBC suites, which have JVM optimizations
                            // already. We should revisit with JDK9.
                            // See also:
                            //  - http://openjdk.java.net/jeps/246
                            //  - https://bugs.openjdk.java.net/secure/attachment/25422/GCM%20Analysis.pdf
                            // CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                            // CipherSuite.TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384,
                            // CipherSuite.TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256,
                            // CipherSuite.TLS_RSA_WITH_AES_256_GCM_SHA384,
                            // CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA256,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
                            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                            CipherSuite.TLS_EMPTY_RENEGOTIATION_INFO_SCSV)
                    .build(),
            ConnectionSpec.CLEARTEXT);

    private FeignOkHttpClients() {
        // factory
    }
}
