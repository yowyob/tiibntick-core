package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.FreelancerRegistrationUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerRegistrationRequest;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerRegistrationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Inbound REST adapter for delivery person registration.
 * Accepts multipart/form-data: JSON text fields + binary photo files.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025
 */
@Slf4j
@RestController
@RequestMapping("/api/freelancers")
@RequiredArgsConstructor
public class FreelancerRegistrationController {

    private final FreelancerRegistrationUseCase registrationUseCase;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FreelancerRegistrationResponse>> register(
            @RequestPart("data") String jsonData,
            @RequestPart(value = "photoCard", required = false) FilePart photoCard,
            @RequestPart(value = "cniRecto", required = false) FilePart cniRecto,
            @RequestPart(value = "cniVerso", required = false) FilePart cniVerso,
            @RequestPart(value = "nuiPhoto", required = false) FilePart nuiPhoto,
            @RequestPart(value = "frontPhoto", required = false) FilePart frontPhoto,
            @RequestPart(value = "backPhoto", required = false) FilePart backPhoto,
            @RequestPart(value = "storefrontPhoto", required = false) FilePart storefrontPhoto) {

        FreelancerRegistrationRequest request;
        try {
            request = objectMapper.readValue(jsonData, FreelancerRegistrationRequest.class);
        } catch (Exception e) {
            log.error("Failed to parse registration JSON data", e);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return registrationUseCase.register(request, photoCard, cniRecto, cniVerso, nuiPhoto, frontPhoto, backPhoto, storefrontPhoto)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }
}
