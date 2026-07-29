package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDTO {
    private UUID id;
    private String lastName;
    private String firstName;
    private Instant createdAt;
}
