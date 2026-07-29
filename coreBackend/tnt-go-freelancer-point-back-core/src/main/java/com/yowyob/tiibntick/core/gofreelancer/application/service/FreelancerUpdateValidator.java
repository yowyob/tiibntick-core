package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerUpdateRequest;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for delivery person update.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Component
public class FreelancerUpdateValidator {

    /**
     * Validates a delivery person update request.
     *
     * Ensures that the requested updates comply with business rules.
     *
     * @param request the update request to validate
     * @return a Mono containing the valid request, or error if invalid
     * @throws ValidationException if validation fails
     */
    public Mono<FreelancerUpdateRequest> validate(FreelancerUpdateRequest request) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            return Mono.error(new ValidationException("Update request cannot be null"));
        }

        // At least one field should be provided for update
        boolean hasAnyField = request.getPhone() != null ||
                request.getCommercialName() != null ||
                request.getCommercialRegister() != null ||
                request.getLogisticsType() != null ||
                request.getBackPhoto() != null ||
                request.getFrontPhoto() != null ||
                request.getPlateNumber() != null ||
                request.getLogisticsClass() != null ||
                request.getColor() != null ||
                request.getTankCapacity() != null ||
                request.getLength() != null ||
                request.getWidth() != null ||
                request.getHeight() != null ||
                request.getUnit() != null ||
                request.getTotalSeatNumber() != null ||
                request.getStreet() != null ||
                request.getCity() != null ||
                request.getDistrict() != null ||
                request.getCountry() != null ||
                request.getDescription() != null;

        if (!hasAnyField) {
            errors.add("At least one field must be provided for update");
        }

        // Return validation result
        if (!errors.isEmpty()) {
            return Mono.error(new ValidationException(String.join(", ", errors)));
        }

        return Mono.just(request);
    }
}
