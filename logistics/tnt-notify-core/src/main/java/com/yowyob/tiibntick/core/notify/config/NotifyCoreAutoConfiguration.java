package com.yowyob.tiibntick.core.notify.config;

import com.yowyob.kernel.i18n.application.port.in.TranslateMessageUseCase;
import com.yowyob.kernel.i18n.config.I18nKernelProperties;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationPreferencesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.out.IKernelDeliveryQueryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationDeliveryPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationPreferencePort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationProviderAdminPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationReminderPort;
import com.yowyob.tiibntick.core.notify.application.port.out.INotificationTemplateAdminPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IPublishNotificationEventPort;
import com.yowyob.tiibntick.core.notify.application.port.out.ISearchNotificationsPort;
import com.yowyob.tiibntick.core.notify.application.port.out.IRecipientLanguagePort;
import com.yowyob.tiibntick.core.notify.application.port.out.ITranslationPort;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n.AccountRecipientLanguageAdapter;
import com.yowyob.tiibntick.core.i18n.application.port.in.ResolveRecipientLanguageUseCase;
import com.yowyob.tiibntick.core.notify.application.service.ManagePreferencesService;
import com.yowyob.tiibntick.core.notify.application.service.NotificationAdminService;
import com.yowyob.tiibntick.core.notify.application.service.SearchNotificationsService;
import com.yowyob.tiibntick.core.notify.application.service.NotificationService;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelDeliveryAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelDeliveryQueryAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelNotificationClient;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelNotificationSearchAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelTemplateRegistrar;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelNotificationPreferenceAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelNotificationProviderConfigAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelNotificationReminderAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel.KernelNotificationTemplateAdapter;
import com.yowyob.tiibntick.core.notify.infrastructure.adapter.i18n.I18nTranslationPortAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
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
public class NotifyCoreAutoConfiguration {

    /**
     * Bridges yow-i18n-kernel's TranslateMessageUseCase into tnt-notify-core's
     * ITranslationPort.
     * Anti-corruption layer isolating the notification domain from the i18n kernel
     * internals. See {@link I18nTranslationPortAdapter} for the language
     * normalization and missing-translation fallback logic — this is the single
     * place every notification send routes through (Audit n°4, P-1/P-2/P-21/P0 item1).
     */
    @Bean
    @ConditionalOnMissingBean
    public ITranslationPort translationPort(TranslateMessageUseCase translateMessageUseCase,
            I18nKernelProperties i18nKernelProperties,
            MeterRegistry meterRegistry) {
        return new I18nTranslationPortAdapter(translateMessageUseCase, i18nKernelProperties, meterRegistry);
    }

    /**
     * Main notification sending use case: hands everything to the Kernel notification engine.
     */
    @Bean
    @ConditionalOnMissingBean
    public ISendNotificationUseCase sendNotificationUseCase(
            ITranslationPort translationPort,
            INotificationDeliveryPort deliveryPort,
            IPublishNotificationEventPort eventPort,
            IRecipientLanguagePort recipientLanguagePort) {
        return new NotificationService(translationPort, deliveryPort, eventPort, recipientLanguagePort);
    }

    /**
     * The language each recipient reads, from their account (tnt-i18n-core). Without it the language carried
     * by the notification model is used.
     */
    @Bean
    @ConditionalOnMissingBean
    public IRecipientLanguagePort recipientLanguagePort(
            org.springframework.beans.factory.ObjectProvider<ResolveRecipientLanguageUseCase> resolver) {
        ResolveRecipientLanguageUseCase use = resolver.getIfAvailable();
        return use != null
                ? new AccountRecipientLanguageAdapter(use)
                : (recipientId, hint) -> reactor.core.publisher.Mono.justOrEmpty(hint);
    }

    /**
     * Notification history use case (served by the Kernel delivery log).
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

    // ── Kernel (RT-comops) notification engine bridge ───────────────────────

    /**
     * Registers the TiiBnTick message templates in the Kernel engine on first use.
     */
    @Bean
    @ConditionalOnMissingBean
    public KernelTemplateRegistrar kernelTemplateRegistrar(KernelNotificationClient client,
            ITranslationPort translationPort) {
        return new KernelTemplateRegistrar(client, translationPort);
    }

    /**
     * Delivery of every channel (email, SMS, WhatsApp, push, in-app) by the Kernel.
     */
    @Bean
    @ConditionalOnMissingBean
    public INotificationDeliveryPort notificationDeliveryPort(KernelNotificationClient client,
            KernelTemplateRegistrar templates, NotifyProperties properties) {
        return new KernelDeliveryAdapter(client, templates, properties.getKernel().isTemplatesEnabled());
    }

    /**
     * User notification preferences, held by the Kernel.
     */
    @Bean
    @ConditionalOnMissingBean
    public INotificationPreferencePort kernelNotificationPreferenceAdapter(KernelNotificationClient client) {
        return new KernelNotificationPreferenceAdapter(client);
    }

    /**
     * Notification history, read from the Kernel delivery log.
     */
    @Bean
    @ConditionalOnMissingBean
    public ISearchNotificationsPort searchNotificationsPort(KernelNotificationClient client) {
        return new KernelNotificationSearchAdapter(client);
    }

    /**
     * Provider/template/reminder/delivery administration ports — always
     * Kernel-backed, since TiiBnTick has no local equivalent for these
     * concerns (they configure the Kernel's own delivery infrastructure).
     */
    @Bean
    @ConditionalOnMissingBean
    public INotificationProviderAdminPort notificationProviderAdminPort(KernelNotificationClient client) {
        return new KernelNotificationProviderConfigAdapter(client);
    }

    @Bean
    @ConditionalOnMissingBean
    public INotificationTemplateAdminPort notificationTemplateAdminPort(KernelNotificationClient client) {
        return new KernelNotificationTemplateAdapter(client);
    }

    @Bean
    @ConditionalOnMissingBean
    public INotificationReminderPort notificationReminderPort(KernelNotificationClient client) {
        return new KernelNotificationReminderAdapter(client);
    }

    @Bean
    @ConditionalOnMissingBean
    public IKernelDeliveryQueryPort kernelDeliveryQueryPort(KernelNotificationClient client) {
        return new KernelDeliveryQueryAdapter(client);
    }

    /**
     * Admin use case exposing providers/templates/reminders/raw-deliveries
     * management to {@code NotificationController}.
     */
    @Bean
    @ConditionalOnMissingBean
    public NotificationAdminService notificationAdminService(
            INotificationProviderAdminPort providerPort,
            INotificationTemplateAdminPort templatePort,
            INotificationReminderPort reminderPort,
            IKernelDeliveryQueryPort deliveryQueryPort) {
        return new NotificationAdminService(providerPort, templatePort, reminderPort, deliveryQueryPort);
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
            @Qualifier("tntObjectMapper") com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            NotifyProperties properties) {
        return new com.yowyob.tiibntick.core.notify.infrastructure.messaging.FreelancerOrgKafkaEventConsumer(
                notificationUseCase, objectMapper, properties);
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