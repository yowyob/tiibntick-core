package com.yowyob.tiibntick.core.billing.wallet.adapter.out.momo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response from MTN MoMo OAuth2 token endpoint.
 * @author MANFOUO Braun
 */
public record MtnTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") int expiresIn
) {}
