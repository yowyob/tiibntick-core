package com.yowyob.kernel.event.adapter.kafka.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CompositeRecordInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

import java.util.Objects;

/**
 * Installs the shared {@link IdempotentRecordInterceptor} on every Kafka listener container
 * factory bean — each module's own factory and Spring Boot's default one alike — so that every
 * {@code @KafkaListener} of the application is idempotent without any per-listener code. An
 * interceptor a factory already carries is kept: the duplicate filter runs first, then it.
 */
public class IdempotentListenerContainerFactoryPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentListenerContainerFactoryPostProcessor.class);

    private final IdempotentRecordInterceptor interceptor;

    public IdempotentListenerContainerFactoryPostProcessor(final IdempotentRecordInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor);
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object postProcessAfterInitialization(final Object bean, final String beanName) {
        if (bean instanceof AbstractKafkaListenerContainerFactory factory) {
            RecordInterceptor existing = factory.getRecordInterceptor();
            if (existing == interceptor) {
                return bean;
            }
            factory.setRecordInterceptor(existing == null
                ? interceptor
                : new CompositeRecordInterceptor(interceptor, existing));
            log.debug("Duplicate filter installed on Kafka listener container factory '{}'", beanName);
        }
        return bean;
    }
}
