package com.yowyob.tiibntick.bootstrap.bridge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Resilience4j silently falls back to its library defaults (1 s time limit, 3 attempts) for an instance name
 * that has no entry in {@code application.yml}. {@code kernelUserWebClient} ran like that and every call made
 * through it (profile photo, user account) timed out after one second. Every instance named in
 * {@link KernelBridgeConfig} must therefore be declared for the circuit breaker, the time limiter and the retry.
 */
@DisplayName("KernelBridgeConfig — every named resilience instance is declared in application.yml")
class KernelResilienceConfigDeclaredTest {

    @Test
    void everyInstanceUsedByTheBridgeHasACircuitBreakerATimeLimiterAndARetry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/yowyob/tiibntick/bootstrap/bridge/KernelBridgeConfig.java"));
        Set<String> used = new TreeSet<>();
        Matcher m = Pattern.compile("resilience\\(\"(\\w+)\"").matcher(source);
        while (m.find()) used.add(m.group(1));
        assertThat(used).contains("kernelWebClient", "kernelUserWebClient", "kernelFileWebClient");

        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        for (String name : used) {
            for (String kind : new String[] {"circuitbreaker", "timelimiter", "retry"}) {
                String prefix = "resilience4j." + kind + ".instances." + name + ".";
                boolean declared = props.stringPropertyNames().stream().anyMatch(k -> k.startsWith(prefix));
                assertThat(declared)
                        .as("%s instance '%s' is missing from application.yml (library defaults would apply)",
                                kind, name)
                        .isTrue();
            }
        }
    }

    @Test
    void fileTransfersAreNotRetriedAndHaveAWiderTimeThanOrdinaryCalls() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties props = yaml.getObject();

        assertThat(props.getProperty("resilience4j.retry.instances.kernelFileWebClient.max-attempts")).isEqualTo("1");
        assertThat(props.getProperty("resilience4j.timelimiter.instances.kernelFileWebClient.timeout-duration"))
                .isEqualTo("35s");
        assertThat(props.getProperty("resilience4j.timelimiter.instances.kernelUserWebClient.timeout-duration"))
                .isEqualTo("5s");
    }
}
