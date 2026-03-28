//package com.example.auction.common.config;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.annotation.PreDestroy;
//import org.apache.http.HttpHost;
//import org.opensearch.client.RestClient;
//import org.opensearch.client.json.jackson.JacksonJsonpMapper;
//import org.opensearch.client.opensearch.OpenSearchClient;
//import org.opensearch.client.transport.OpenSearchTransport;
//import org.opensearch.client.transport.rest_client.RestClientTransport;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//@ConditionalOnProperty(name = "opensearch.enabled", havingValue = "true")
//public class OpenSearchConfig {
//
//    @Value("${opensearch.host}")
//    private String host;
//
//    @Value("${opensearch.port}")
//    private int port;
//
//    @Value("${opensearch.scheme:http}")
//    private String scheme;
//
//    private RestClient restClient;
//    private OpenSearchTransport transport;
//
//    @Bean
//    public OpenSearchClient openSearchClient(ObjectMapper objectMapper) {
//        this.restClient = RestClient.builder(
//                new HttpHost(host, port, scheme)
//        ).build();
//
//        this.transport = new RestClientTransport(
//                restClient,
//                new JacksonJsonpMapper(objectMapper)
//        );
//
//        return new OpenSearchClient(transport);
//    }
//
//    @PreDestroy
//    public void close() throws Exception {
//        if (transport != null) transport.close();
//        if (restClient != null) restClient.close();
//    }
//}