package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO (Data Transfer Object) representing a subscription request.
 * Contains the unique identifier of the delivery person initiating the request.
 *
 * @author François-Charles ATANGA
 * @date 04/02/2026
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRequestDTO {
    private UUID freelancerId;
}
