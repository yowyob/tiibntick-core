package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SetPasswordRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Validator for setting password.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Component
public class SetPasswordValidator {

    public Mono<Void> validate(SetPasswordRequest request) {
        // TODO: Implement validation logic (e.g., password strength)
        return Mono.empty();
    }
}
