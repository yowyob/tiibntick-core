package com.yowyob.tiibntick.core.incident.port.inbound;
import com.yowyob.tiibntick.core.incident.application.command.ReportDriverWithdrawalCommand;
import com.yowyob.tiibntick.core.incident.domain.model.Incident;
import reactor.core.publisher.Mono;
/**
 * Inbound port: report a driver withdrawal from an active mission.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IReportDriverWithdrawalUseCase {
    Mono<Incident> execute(ReportDriverWithdrawalCommand command);
}
