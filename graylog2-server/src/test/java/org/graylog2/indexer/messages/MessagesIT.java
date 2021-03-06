/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.indexer.messages;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.Maps;
import joptsimple.internal.Strings;
import org.graylog.testing.elasticsearch.ElasticsearchBaseTest;
import org.graylog2.indexer.IndexSet;
import org.graylog2.indexer.TestIndexSet;
import org.graylog2.indexer.indexset.IndexSetConfig;
import org.graylog2.indexer.retention.strategies.DeletionRetentionStrategy;
import org.graylog2.indexer.retention.strategies.DeletionRetentionStrategyConfig;
import org.graylog2.indexer.rotation.strategies.MessageCountRotationStrategy;
import org.graylog2.indexer.rotation.strategies.MessageCountRotationStrategyConfig;
import org.graylog2.plugin.Message;
import org.graylog2.system.processing.ProcessingStatusRecorder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public abstract class MessagesIT extends ElasticsearchBaseTest {
    private static final String INDEX_NAME = "messages_it_deflector";

    protected Messages messages;

    private static final IndexSetConfig indexSetConfig = IndexSetConfig.builder()
            .id("index-set-1")
            .title("Index set 1")
            .description("For testing")
            .indexPrefix("messages_it")
            .creationDate(ZonedDateTime.now(ZoneOffset.UTC))
            .shards(1)
            .replicas(0)
            .rotationStrategyClass(MessageCountRotationStrategy.class.getCanonicalName())
            .rotationStrategy(MessageCountRotationStrategyConfig.createDefault())
            .retentionStrategyClass(DeletionRetentionStrategy.class.getCanonicalName())
            .retentionStrategy(DeletionRetentionStrategyConfig.createDefault())
            .indexAnalyzer("standard")
            .indexTemplateName("template-1")
            .indexOptimizationMaxNumSegments(1)
            .indexOptimizationDisabled(false)
            .build();
    private static final IndexSet indexSet = new TestIndexSet(indexSetConfig);

    protected abstract MessagesAdapter createMessagesAdapter(MetricRegistry metricRegistry);

    @Before
    public void setUp() throws Exception {
        client().deleteIndices(INDEX_NAME);
        client().createIndex(INDEX_NAME);
        client().waitForGreenStatus(INDEX_NAME);
        final MetricRegistry metricRegistry = new MetricRegistry();
        messages = new Messages(mock(TrafficAccounting.class), createMessagesAdapter(metricRegistry), mock(ProcessingStatusRecorder.class));
    }

    @After
    public void tearDown() {
        client().deleteIndices(INDEX_NAME);
    }

    @Test
    public void testIfTooLargeBatchesGetSplitUp() throws Exception {
        // This test assumes that ES is configured with bulk_max_body_size to 100MB
        // Check if we can index about 300MB of messages (once the large batch gets split up)
        final int MESSAGECOUNT = 303;
        // Each Message is about 1 MB
        final ArrayList<Map.Entry<IndexSet, Message>> largeMessageBatch = createMessageBatch(1024 * 1024, MESSAGECOUNT);
        final List<String> failedItems = this.messages.bulkIndex(largeMessageBatch);

        assertThat(failedItems).isEmpty();

        Thread.sleep(2000); // wait for ES to finish indexing

        assertThat(messageCount(INDEX_NAME)).isEqualTo(MESSAGECOUNT);
    }

    protected abstract Double messageCount(String indexName);

    @Test
    public void unevenTooLargeBatchesGetSplitUp() throws Exception {
        final int MESSAGECOUNT = 100;
        final int LARGE_MESSAGECOUNT = 20;
        final ArrayList<Map.Entry<IndexSet, Message>> messageBatch = createMessageBatch(1024, MESSAGECOUNT);
        messageBatch.addAll(createMessageBatch(1024 * 1024 * 5, LARGE_MESSAGECOUNT));
        final List<String> failedItems = this.messages.bulkIndex(messageBatch);

        assertThat(failedItems).isEmpty();

        Thread.sleep(2000); // wait for ES to finish indexing

        assertThat(messageCount(INDEX_NAME)).isEqualTo(MESSAGECOUNT + LARGE_MESSAGECOUNT);
    }

    private ArrayList<Map.Entry<IndexSet, Message>> createMessageBatch(int size, int count) {
        final ArrayList<Map.Entry<IndexSet, Message>> messageList = new ArrayList<>();

        final String message = Strings.repeat('A', size);
        for (int i = 0; i < count; i++) {
            messageList.add(Maps.immutableEntry(indexSet, new Message(i + message, "source", DateTime.now(DateTimeZone.UTC))));
        }
        return messageList;
    }
}
