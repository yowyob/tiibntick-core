package com.yowyob.tiibntick.core.notify.config;

import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationPreferencesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.IMessageProviderPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationPreferencePort;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ISearchNotificationsPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationRepositoryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.application.service.ManagePreferencesService;
import com.yowyob.tiibntick.core.notify.application.service.SearchNotificationsService;
import com.yowyob.tiibntick.core.notify.application.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Boot auto-configuration for the tnt-notify-core module.
 * Wires all notification use cases and adapters via the hexagonal architecture
 * ports.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
@AutoConfiguration
@EnableConfigurationProperties(NotifyProperties.class)
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.notify.infrastructure.persistence.repository")
public class NotifyCoreAutoConfiguration {

    /**
     * Bridges yow-i18n-kernel's TranslateMessageUseCase into tnt-notify-core's
     * ITranslationPort.
     * Anti-corruption layer isolating the notification domain from the i18n kernel
     * internals.
     */
    @Bean
    @ConditionalOnMissingBean
    public ITranslationPort translationPort(TranslateMessageUseCase translateMessageUseCase) {
        return model -> Mono.justOrEmpty(
                translateMessageUseCase.translate(
                        model.templateKey(),
                        model.targetLanguage(),
                        model.parameters()))
                .defaultIfEmpty("⚠ Missing translation: " + model.templateKey());
    }

    /**
     * Main notification sending use case.
     */
    @Bean
    @ConditionalOnMissingBean
    public ISendNotificationUseCase sendNotificationUseCase(
            INotificationRepositoryPort repository,
            ITranslationPort translationPort,
            List<IMessageProviderPort> providers,
            IPublishNotificationEventPort eventPort) {
        return new NotificationService(repository, translationPort, providers, eventPort);
    }

    /**
     * Notification query use case.
     */
    @Bean
    @ConditionalOnMissingBean
    public ISearchNotificationsUseCase searchNotificationsUseCase(
            ISearchNotificationsPort searchPort) {
        return new SearchNotificationsService(searchPort);
    }

    /**
     * Notification preference management use case.
     */
    @Bean
    @ConditionalOnMissingBean
    public IManageNotificationPreferencesUseCase managePreferencesUseCase(
            INotificationPreferencePort preferencePort) {
        return new ManagePreferencesService(preferencePort);
    }

    /**
     * Registers the FreelancerOrg Kafka event consumer ().
     * Consumes events from tnt-administration-core and tnt-delivery-core
     * related to FreelancerOrg lifecycle, missions, and billing template
     * operations.
     */
    @Bean
    @ConditionalOnMissingBean(com.yowyob.tiibntick.core.notify.infrastructure.messaging.FreelancerOrgKafkaEventConsumer.class)
    public com.yowyob.tiibntick.core.notify.infrastructure.messaging.FreelancerOrgKafkaEventConsumer freelancerOrgKafkaEventConsumer(
            com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase notificationUseCase,
            @Qualifier("tntObjectMapper") com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new com.yowyob.tiibntick.core.notify.infrastructure.messaging.FreelancerOrgKafkaEventConsumer(
                notificationUseCase, objectMapper);
    }

    /**
     * Kafka consumer factory backing {@code FreelancerOrgKafkaEventConsumer}'s listeners.
     */
    @Bean("notifyConsumerFactory")
    @ConditionalOnMissingBean(name = "notifyConsumerFactory")
    public ConsumerFactory<String, String> notifyConsumerFactory(Environment env) {
        String bootstrapServers = env.getProperty(
                "spring.kafka.bootstrap-servers", "localhost:9092");
        String groupId = env.getProperty(
                "spring.kafka.consumer.group-id", "tnt-notify-core");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Kafka listener container factory referenced by
     * {@code @KafkaListener(containerFactory = "notifyKafkaListenerContainerFactory")}.
     */
    @Bean("notifyKafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "notifyKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> notifyKafkaListenerContainerFactory(
            Environment env) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notifyConsumerFactory(env));
        factory.setConcurrency(2);
        return factory;
    }

}