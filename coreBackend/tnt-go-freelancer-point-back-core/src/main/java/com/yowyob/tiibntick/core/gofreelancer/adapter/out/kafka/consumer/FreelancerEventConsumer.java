package com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.consumer;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerCreatedEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.FreelancerValidatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inbound Kafka adapter: consumes delivery person lifecycle events.
 * Logs events for monitoring and debugging purposes.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Slf4j
@Component
public class FreelancerEventConsumer {

    @KafkaListener(topics = "delivery-person-created", groupId = "tiibntick-group")
    public void handleFreelancerCreated(FreelancerCreatedEvent event) {
        log.info("Received FreelancerCreatedEvent: freelancerId={}, email={}",
                event.getFreelancerId(), event.getEmail());
    }

    @KafkaListener(topics = "delivery-person-validated", groupId = "tiibntick-group")
    public void handleFreelancerValidated(FreelancerValidatedEvent event) {
        log.info("Received FreelancerValidatedEvent: freelancerId={}, approved={}",
                event.getFreelancerId(), event.isApproved());
    }
}
