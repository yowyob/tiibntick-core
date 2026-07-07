package com.yowyob.tiibntick.core.actor.application.port.in;

import com.yowyob.tiibntick.core.actor.application.command.AssignMissionCommand;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import reactor.core.publisher.Mono;

public interface IAssignMissionToDelivererUseCase {
    Mono<DelivererProfile> assignMission(AssignMissionCommand command);
}
