package com.yowyob.tiibntick.core.gofreelancer.domain.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerRegistrationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerRegistrationResponse;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * Inbound port for delivery person registration use case.
 */
public interface FreelancerRegistrationUseCase {

    Mono<FreelancerRegistrationResponse> register(
            FreelancerRegistrationRequest request,
            FilePart photoCard,
            FilePart cniRecto,
            FilePart cniVerso,
            FilePart nuiPhoto,
            FilePart frontPhoto,
            FilePart backPhoto,
            FilePart storefrontPhoto);
}
