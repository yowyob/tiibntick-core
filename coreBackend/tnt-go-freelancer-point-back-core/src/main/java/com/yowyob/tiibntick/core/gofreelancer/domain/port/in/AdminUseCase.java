package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DashboardStatsDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthResponseDTO;
import reactor.core.publisher.Mono;

/**
 * Inbound port for admin management use cases.
 */
public interface AdminUseCase {

    Mono<AuthResponseDTO> getCurrentAdmin(String email);

    Mono<DashboardStatsDTO> getDashboardStats();
}
