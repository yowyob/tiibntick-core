package com.yowyob.tiibntick.core.accounting.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.accounting.application.port.in.PostJournalEntryCommand;
import com.yowyob.tiibntick.core.accounting.application.service.AccountingApplicationService;
import com.yowyob.tiibntick.core.accounting.infrastructure.config.AccountingCoreConfiguration;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * In-process (embedded broker) integration test proving the Audit n5 · P-02 fix: before this
 * change, {@code BillingEventAccountingConsumer}'s {@code @KafkaListener} methods had no
 * {@code containerFactory}, so Spring Kafka fell back to the autoconfigured
 * {@code kafkaListenerContainerFactory}, whose value-deserializer is
 * {@code ByteArrayDeserializer} (tnt-bootstrap's application.yml) — incompatible with the
 * listeners' {@code ConsumerRecord<String, String>} signature, so the message would never
 * actually reach {@code AccountingApplicationService}.
 *
 * <p>This test wires the real {@code accountingKafkaConsumerFactory} /
 * {@code accountingKafkaListenerContainerFactory} beans (as fixed, called directly on
 * {@link AccountingCoreConfiguration} rather than loading its {@code @AutoConfiguration}'s
 * component scan / R2DBC repositories, which are out of scope for this test) plus the real
 * {@code BillingEventAccountingConsumer}, publishes a JSON string payload on
 * {@code tnt.billing.wallet.commission.calculated} through a plain {@code KafkaTemplate},
 * and asserts the listener actually deserializes it and invokes
 * {@code AccountingApplicationService.postJournalEntry(...)} — proof the String deserializer
 * is now in effect end-to-end, not just present in the config class.
 *
 * @author MANFOUO Braun
 */
@SpringJUnitConfig
@EmbeddedKafka(partitions = 1, topics = {"tnt.billing.wallet.commission.calculated"})
@Tag("integration")
class BillingEventAccountingConsumerDeserializerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> producerTemplate;

    @Autowired
    private AccountingApplicationService accountingService;

    @Test
    void commissionCalculatedListenerDeserializesStringPayload() {
        UUID tenantId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        String missionId = UUID.randomUUID().toString();
        String payload = """
                {"tenantId":"%s","organizationId":"%s","missionId":"%s","commission":"1500"}
                """.formatted(tenantId, organizationId, missionId);

        producerTemplate.send("tnt.billing.wallet.commission.calculated", missionId, payload);

        verify(accountingService, timeout(15000)).postJournalEntry(any(PostJournalEntryCommand.class));
    }

    @Configuration
    @EnableKafka
    static class TestConfig {

        @Bean
        AccountingApplicationService accountingApplicationService() {
            AccountingApplicationService mockService = mock(AccountingApplicationService.class);
            when(mockService.postJournalEntry(any())).thenReturn(Mono.empty());
            return mockService;
        }

        @Bean("tntObjectMapper")
        ObjectMapper tntObjectMapper() {
            return new ObjectMapper();
        }

        @Bean
        BillingEventAccountingConsumer billingEventAccountingConsumer(
                AccountingApplicationService accountingApplicationService,
                ObjectMapper tntObjectMapper) {
            return new BillingEventAccountingConsumer(accountingApplicationService, tntObjectMapper);
        }

        @Bean("accountingKafkaConsumerFactory")
        ConsumerFactory<String, String> accountingKafkaConsumerFactory(EmbeddedKafkaBroker broker) {
            return new AccountingCoreConfiguration()
                    .accountingKafkaConsumerFactory(broker.getBrokersAsString(), "tnt-accounting-core-it");
        }

        @Bean("accountingKafkaListenerContainerFactory")
        ConcurrentKafkaListenerContainerFactory<String, String> accountingKafkaListenerContainerFactory(
                ConsumerFactory<String, String> accountingKafkaConsumerFactory) {
            return new AccountingCoreConfiguration()
                    .accountingKafkaListenerContainerFactory(accountingKafkaConsumerFactory);
        }

        @Bean
        KafkaTemplate<String, String> producerTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        }
    }
}
