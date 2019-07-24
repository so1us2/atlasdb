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

package com.palantir.atlasdb.timelock.watch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.palantir.atlasdb.timelock.watch.trie.LousyPrefixTrieImpl;
import com.palantir.atlasdb.timelock.watch.trie.PrefixTrie;

public class LousyPrefixTrieImplTest {
    @Test
    public void trie() {
        PrefixTrie<Integer> prefixTrie = new LousyPrefixTrieImpl<>();

        prefixTrie.add("a", 1);
        prefixTrie.add("ab", 2);
        prefixTrie.add("abc", 3);
        prefixTrie.add("abcd", 4);
        prefixTrie.add("abfahren", 5);
        prefixTrie.add("abflug", 6);
        prefixTrie.add("b", 7);

        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("a")).containsExactlyInAnyOrder(1);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("abc")).containsExactlyInAnyOrder(1, 2, 3);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("abcd")).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("abcdabcdabcd")).containsExactlyInAnyOrder(1, 2, 3, 4);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("abfahren")).containsExactlyInAnyOrder(1, 2, 5);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("abfahrenUUIDtq19")).containsExactlyInAnyOrder(1, 2, 5);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("begegnungen")).containsExactlyInAnyOrder(7);
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("cacophonous")).containsExactlyInAnyOrder();
        assertThat(prefixTrie.findDataInTrieWithKeysPrefixesOf("")).containsExactlyInAnyOrder();
    }
}
