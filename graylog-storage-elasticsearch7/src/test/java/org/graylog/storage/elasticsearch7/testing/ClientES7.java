package org.graylog.storage.elasticsearch7.testing;

import com.fasterxml.jackson.databind.JsonNode;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.admin.indices.alias.IndicesAliasesRequest.AliasActions;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.bulk.BulkRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.index.IndexRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.support.IndicesOptions;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.action.support.WriteRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.CloseIndexRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.CreateIndexRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.GetIndexRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.GetIndexResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.GetIndexTemplatesResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.GetMappingsRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.GetMappingsResponse;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.IndexTemplateMetaData;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.common.settings.Settings;
import org.graylog.storage.elasticsearch7.ElasticsearchClient;
import org.graylog.testing.elasticsearch.BulkIndexRequest;
import org.graylog.testing.elasticsearch.Client;

import java.util.Map;

public class ClientES7 implements Client {
    private final ElasticsearchClient client;

    public ClientES7(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void createIndex(String index, int shards, int replicas) {
        final CreateIndexRequest createIndexRequest = new CreateIndexRequest(index);
        createIndexRequest.settings(
                Settings.builder()
                        .put("index.number_of_shards", shards)
                        .put("index.number_of_replicas", replicas)
        );

        client.execute((c, requestOptions) -> c.indices().create(createIndexRequest, requestOptions));
    }

    @Override
    public void deleteIndices(String... indices) {
        for (String index : indices)
            if (indicesExists(index)) {
                final DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(index);
                client.execute((c, requestOptions) -> c.indices().delete(deleteIndexRequest, requestOptions));
            }
    }

    @Override
    public void closeIndex(String index) {
        final CloseIndexRequest closeIndexRequest = new CloseIndexRequest(index);
        client.execute((c, requestOptions) -> c.indices().close(closeIndexRequest, requestOptions));
    }

    @Override
    public boolean indicesExists(String... indices) {
        final GetIndexRequest getIndexRequest = new GetIndexRequest(indices);
        return client.execute((c, requestOptions) -> c.indices().exists(getIndexRequest, requestOptions));
    }

    @Override
    public void addAliasMapping(String indexName, String alias) {
        final IndicesAliasesRequest indicesAliasesRequest = new IndicesAliasesRequest();
        final AliasActions aliasAction = new AliasActions(AliasActions.Type.ADD)
                .index(indexName)
                .alias(alias);
        indicesAliasesRequest.addAliasAction(aliasAction);

        client.execute((c, requestOptions) -> c.indices().updateAliases(indicesAliasesRequest, requestOptions),
                "failed to add alias " + alias + " for index " + indexName);
    }

    @Override
    public boolean isSourceEnabled(String testIndexName) {
        final GetMappingsResponse mapping = getMapping(testIndexName);
        return true;
    }

    @Override
    public String fieldType(String testIndexName, String source) {
        final GetMappingsResponse mapping = getMapping(testIndexName);
        return mapping.mappings().get("source").type();
    }

    private GetMappingsResponse getMapping(String... indices) {
        final GetMappingsRequest request = new GetMappingsRequest().indices(indices);
        return client.execute((c, requestOptions) -> c.indices().getMapping(request, requestOptions));
    }

    @Override
    public JsonNode getTemplate(String templateName) {
        final GetIndexTemplatesRequest request = new GetIndexTemplatesRequest(templateName);
        final GetIndexTemplatesResponse result = client.execute((c, requestOptions) -> c.indices().getIndexTemplate(request, requestOptions));
        return null;
    }

    @Override
    public void putTemplate(String templateName, Map<String, Object> source) {
        final PutIndexTemplateRequest request = new PutIndexTemplateRequest(templateName).source(source);
        client.execute((c, requestOptions) -> c.indices().putTemplate(request, requestOptions),
                "Unable to put template " + templateName);
    }

    @Override
    public void deleteTemplates(String... templates) {
        for (String template : templates) {
            final DeleteIndexTemplateRequest deleteIndexTemplateRequest = new DeleteIndexTemplateRequest(template);
            client.execute((c, requestOptions) -> c.indices().deleteTemplate(deleteIndexTemplateRequest, requestOptions));
        }
    }

    @Override
    public void waitForGreenStatus(String... indices) {
        final ClusterHealthRequest clusterHealthRequest = new ClusterHealthRequest(indices);
        clusterHealthRequest.waitForGreenStatus();

        client.execute((c, requestOptions) -> c.cluster().health(clusterHealthRequest, requestOptions));
    }

    @Override
    public void refreshNode() {
        final RefreshRequest refreshRequest = new RefreshRequest();
        client.execute((c, requestOptions) -> c.indices().refresh(refreshRequest, requestOptions));
    }

    @Override
    public void bulkIndex(BulkIndexRequest bulkIndexRequest) {
        final BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        bulkIndexRequest.requests()
                .forEach((indexName, documents) -> documents
                        .forEach(doc -> bulkRequest.add(createIndexRequest(indexName, doc))));
        client.execute((c, requestOptions) -> c.bulk(bulkRequest, requestOptions));
    }

    private IndexRequest createIndexRequest(String indexName, Map<String, Object> source) {
        return new IndexRequest(indexName)
                .source(source);
    }

    @Override
    public void cleanUp() {
        deleteIndices(existingIndices());
        deleteTemplates(existingTemplates());
    }

    private String[] existingTemplates() {
        final GetIndexTemplatesRequest getIndexTemplatesRequest = new GetIndexTemplatesRequest("*");
        final GetIndexTemplatesResponse result = client.execute((c, requestOptions) -> c.indices().getIndexTemplate(getIndexTemplatesRequest, requestOptions));
        return result.getIndexTemplates().stream()
                .map(IndexTemplateMetaData::name)
                .toArray(String[]::new);
    }

    private String[] existingIndices() {
        final GetIndexRequest getIndexRequest = new GetIndexRequest("*");
        getIndexRequest.indicesOptions(IndicesOptions.lenientExpandOpen());
        final GetIndexResponse result = client.execute((c, requestOptions) -> c.indices().get(getIndexRequest, requestOptions));
        return result.getIndices();
    }
}
