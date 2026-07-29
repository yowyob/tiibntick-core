package com.yowyob.tiibntick.core.gofreelancer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the DJL sentiment analysis model.
 *
 * <p>Bound to the {@code tnt.gofp.sentiment.*} namespace in {@code tnt-bootstrap/application.yml}.
 *
 * <pre>
 * tnt.gofp.sentiment.model-name=nlptown/bert-base-multilingual-uncased-sentiment
 * tnt.gofp.sentiment.cache-dir=/app/.djl.ai
 * tnt.gofp.sentiment.enabled=true
 * </pre>
 *
 * @author François-Charles ATANGA
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "tnt.gofp.sentiment")
public class SentimentModelConfig {

    /**
     * HuggingFace model identifier.
     * Default: nlptown/bert-base-multilingual-uncased-sentiment
     * (outputs 1-5 stars directly, multilingual including French)
     */
    private String modelName = "nlptown/bert-base-multilingual-uncased-sentiment";

    /**
     * Directory where DJL caches downloaded models.
     * In Docker this should be a mounted volume to persist across restarts.
     */
    private String cacheDir = "${user.home}/.djl.ai";

    /**
     * Whether DJL-based sentiment analysis is enabled.
     * When false the keyword-based fallback is always used.
     */
    private boolean enabled = true;
}
