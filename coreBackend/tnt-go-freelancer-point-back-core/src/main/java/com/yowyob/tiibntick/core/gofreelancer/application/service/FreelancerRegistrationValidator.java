package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerRegistrationRequest;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validator for delivery person registration.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Component
public class FreelancerRegistrationValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final int MIN_PASSWORD_LENGTH = 6;

    /**
     * Validates a delivery person registration request.
     *
     * Checks for data integrity, required fields, and format constraints.
     *
     * @param request the registration request to validate
     * @return a Mono containing the valid request, or error if invalid
     * @throws ValidationException if validation fails
     */
    public Mono<FreelancerRegistrationRequest> validate(FreelancerRegistrationRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            return Mono.error(new ValidationException("Registration request cannot be null"));
        }

        // Required fields validation
        if (isBlank(request.getFirstName())) {
            errors.add("First name is required");
        }

        if (isBlank(request.getLastName())) {
            errors.add("Last name is required");
        }

        if (isBlank(request.getEmail())) {
            errors.add("Email is required");
        } else if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
            errors.add("Email format is invalid");
        }

        if (isBlank(request.getPhone())) {
            errors.add("Phone is required");
        }

        if (isBlank(request.getPassword())) {
            errors.add("Password is required");
        } else if (request.getPassword().length() < MIN_PASSWORD_LENGTH) {
            errors.add("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        // Return validation result
        if (!errors.isEmpty()) {
            return Mono.error(new ValidationException(String.join(", ", errors)));
        }

        return Mono.just(request);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
