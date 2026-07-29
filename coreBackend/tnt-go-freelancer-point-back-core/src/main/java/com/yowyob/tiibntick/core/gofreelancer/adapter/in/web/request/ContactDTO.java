package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import java.util.UUID;
import lombok.Data;

@Data
public class ContactDTO {
    private UUID id;
    private UUID userId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
}
