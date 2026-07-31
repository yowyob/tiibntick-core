package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPricingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FreelancerPricingRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerPricingPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * CRUD for {@link FreelancerPricingPolicy}.
 *
 * @author MANFOUO BRAUN
 */
@Service
@RequiredArgsConstructor
public class FreelancerPricingPolicyApplicationService implements FreelancerPricingPolicyUseCase {

    private final FreelancerPricingRepository freelancerPricingRepository;

    @Override
    public Mono<FreelancerPricingDTO> getPolicy(UUID freelancerId) {
        return freelancerPricingRepository.findByDeliveryPersonId(freelancerId)
                .map(this::toDto);
    }

    @Override
    public Mono<FreelancerPricingDTO> upsertPolicy(UUID freelancerId, FreelancerPricingDTO request) {
        return freelancerPricingRepository.findByDeliveryPersonId(freelancerId)
                .defaultIfEmpty(FreelancerPricingPolicy.builder()
                        .deliveryPersonId(freelancerId)
                        .build())
                .flatMap(existing -> {
                    if (request.getPricePerKg() != null) existing.setPricePerKg(request.getPricePerKg());
                    if (request.getPricePerCbm() != null) existing.setPricePerCbm(request.getPricePerCbm());
                    if (request.getPricePerKm() != null) existing.setPricePerKm(request.getPricePerKm());
                    if (request.getFragileSurcharge() != null) {
                        existing.setFragileSurcharge(request.getFragileSurcharge());
                    }
                    if (request.getPerishableSurcharge() != null) {
                        existing.setPerishableSurcharge(request.getPerishableSurcharge());
                    }
                    if (request.getBaseFee() != null) existing.setBaseFee(request.getBaseFee());
                    if (request.getCurrency() != null) existing.setCurrency(request.getCurrency());
                    if (existing.getPricePerKg() == null) existing.setPricePerKg(0.0);
                    if (existing.getPricePerCbm() == null) existing.setPricePerCbm(0.0);
                    if (existing.getPricePerKm() == null) existing.setPricePerKm(0.0);
                    if (existing.getFragileSurcharge() == null) existing.setFragileSurcharge(0.0);
                    if (existing.getPerishableSurcharge() == null) existing.setPerishableSurcharge(0.0);
                    if (existing.getBaseFee() == null) existing.setBaseFee(0.0);
                    if (existing.getCurrency() == null) existing.setCurrency("XAF");
                    return freelancerPricingRepository.save(existing);
                })
                .map(this::toDto);
    }

    private FreelancerPricingDTO toDto(FreelancerPricingPolicy p) {
        return FreelancerPricingDTO.builder()
                .pricePerKg(p.getPricePerKg())
                .pricePerCbm(p.getPricePerCbm())
                .pricePerKm(p.getPricePerKm())
                .fragileSurcharge(p.getFragileSurcharge())
                .perishableSurcharge(p.getPerishableSurcharge())
                .baseFee(p.getBaseFee())
                .currency(p.getCurrency() != null ? p.getCurrency() : "XAF")
                .build();
    }
}
