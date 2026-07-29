package com.yowyob.tiibntick.core.gofreelancer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;

/**
 * Configuration class for Elasticsearch connection.
 * Configures the reactive client and repository settings.
 *
 * @author François-Charles ATANGA
 * @date 05/02/2026
 */
@Configuration
@EnableReactiveElasticsearchRepositories(basePackages = "com.yowyob.tiibntick.core.gofreelancer.adapter.out.search")
@ConditionalOnProperty(name = "spring.elasticsearch.enabled", havingValue = "true", matchIfMissing = true)
public class ElasticsearchConfig extends ReactiveElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:localhost:9200}")
    private String elasticsearchUri;

    @Override
    public ClientConfiguration clientConfiguration() {
        // Remove "http://" or "https://" prefix if present, as ClientConfiguration
        // expects host:port
        String connectionString = elasticsearchUri.replace("http://", "").replace("https://", "");

        return ClientConfiguration.builder()
                .connectedTo(connectionString)
                .withConnectTimeout(java.time.Duration.ofSeconds(10))
                .withSocketTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }
}
