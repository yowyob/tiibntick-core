package com.yowyob.tiibntick.core.billing.wallet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.kernel.KernelPaymentGatewayAdapter;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IKernelPaymentGatewayPort;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

/**
 * WalletModuleConfig — Spring configuration for tnt-billing-wallet.
 * Registers the Kernel payment gateway fallback WebClient/port, Kafka consumer,
 * and JSON ObjectMapper. (The module's dedicated Kafka producer was removed in the
 * Chantier C · Audit n°3 · P5 outbox migration — {@code WalletKafkaPublisher} now goes
 * through yow-event-kernel's transactional outbox.)
 *
 * <p>Direct MTN/Orange/Stripe WebClients were removed as part of the payment/wallet
 * Kernel-delegation workstream (step 6) — provider integration is now exclusively the
 * Kernel's responsibility; see {@code KernelPaymentGatewayAdapter}.
 *
 * @author MANFOUO Braun
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.repository")
@EnableScheduling
public class WalletModuleConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    @Value("${tnt.kernel.base-url:https://kernel-core.yowyob.com}")
    private String kernelBaseUrl;

    /**
     * Fallback Kernel payment WebClient, only used if {@code tnt-bootstrap}'s
     * {@code KernelBridgeConfig} hasn't already defined the {@code kernelPaymentWebClient}
     * bean (e.g. running this module standalone in tests). In the real app, the bootstrap
     * bean always wins — see {@code KernelBridgeConfig} javadoc.
     */
    @Bean("kernelPaymentWebClient")
    @ConditionalOnMissingBean(name = "kernelPaymentWebClient")
    public WebClient kernelPaymentWebClient() {
        return WebClient.builder()
                .baseUrl(kernelBaseUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public IKernelPaymentGatewayPort kernelPaymentGatewayPort(WebClient kernelPaymentWebClient) {
        return new KernelPaymentGatewayAdapter(kernelPaymentWebClient);
    }

    @Bean
    public ObjectMapper walletObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public ConsumerFactory<String, String> walletConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "tnt-billing-wallet");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> walletKafkaListenerContainerFactory(
            ConsumerFactory<String, String> walletConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(walletConsumerFactory);
        return factory;
    }
}