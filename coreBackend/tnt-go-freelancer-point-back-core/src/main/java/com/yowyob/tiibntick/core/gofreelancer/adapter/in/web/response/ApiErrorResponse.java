package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API error response DTO.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {
    private String error;
    private String message;
    private int status;
}
