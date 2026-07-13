package com.yowyob.tiibntick.core.linkback.application.port.in;

import com.yowyob.tiibntick.core.linkback.application.port.in.command.ReportNetworkAlertCommand;
import com.yowyob.tiibntick.core.linkback.domain.model.NetworkAlert;
import reactor.core.publisher.Mono;

public interface ReportNetworkAlertUseCase {
    Mono<NetworkAlert> report(ReportNetworkAlertCommand command);
}
