package org.graylog.storage.elasticsearch7.cat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Streams;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.Request;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.RequestOptions;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.Response;
import org.graylog.shaded.elasticsearch7.org.elasticsearch.client.RestHighLevelClient;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CatApi {
    private final ObjectMapper objectMapper;

    @Inject
    public CatApi(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<NodeResponse> nodes(RestHighLevelClient c, RequestOptions requestOptions) throws IOException {
        final Request request = request("GET", "nodes", requestOptions);
        request.addParameter("h", "id,name,host,ip,fileDescriptorMax,diskUsed,diskTotal,diskUsedPercent");
        request.addParameter("full_id", "true");
        return perform(c, request, new TypeReference<List<NodeResponse>>() {});
    }

    public Set<String> indices(RestHighLevelClient c, String index, Collection<String> status, RequestOptions requestOptions) throws IOException {
        return indices(c, Collections.singleton(index), status, requestOptions);
    }

    public Set<String> indices(RestHighLevelClient c, Collection<String> indices, Collection<String> status, RequestOptions requestOptions) throws IOException {
        final String joinedIndices = String.join(",", indices);
        final Request request = request("GET", "indices/" + joinedIndices, requestOptions);
        request.addParameter("h", "index,status");
        request.addParameter("expand_wildcards", "all");
        request.addParameter("s", "index,status");

        final Response response = c.getLowLevelClient().performRequest(request);
        final JsonNode jsonResponse = objectMapper.readTree(response.getEntity().getContent());

        return Streams.stream(jsonResponse.elements())
                .filter(index -> status.isEmpty() || status.contains(index.path("status").asText()))
                .map(index -> index.path("index").asText())
                .collect(Collectors.toSet());
    }

    public Optional<String> indexState(RestHighLevelClient c, RequestOptions requestOptions, String indexName) throws IOException {
        final Request request = request("GET", "indices/" + indexName, requestOptions);
        request.addParameter("h", "index,status");
        request.addParameter("expand_wildcards", "all");
        request.addParameter("s", "index,status");

        final Response response = c.getLowLevelClient().performRequest(request);
        final JsonNode jsonResponse = objectMapper.readTree(response.getEntity().getContent());

        return Streams.stream(jsonResponse.elements())
                .filter(index -> index.path("index").asText().equals(indexName))
                .map(index -> index.path("status").asText())
                .findFirst();
    }

    private <R> R perform(RestHighLevelClient c, Request request, TypeReference<R> responseClass) throws IOException {
        final Response response = c.getLowLevelClient().performRequest(request);

        return returnType(response, responseClass);
    }

    private <R> R returnType(Response response, TypeReference<R> responseClass) throws IOException {
        return objectMapper.readValue(response.getEntity().getContent(), responseClass);
    }

    private Request request(String method, String endpoint, RequestOptions requestOptions) {
        final Request request = new Request(method, "/_cat/" + endpoint);
        request.addParameter("format", "json");
        request.setOptions(requestOptions);

        return request;
    }
}
