package com.yowyob.tiibntick.core.agency.org.application.mapper;

import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyBranchResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRegistryResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRelayHubResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencySettingsResponse;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyBranchEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRelayHubEntity;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencySettingsEntity;

public final class AgencyOrgMapper {

    private AgencyOrgMapper() {}

    public static AgencyRegistryResponse toAgencyResponse(AgencyRegistryEntity e) {
        return new AgencyRegistryResponse(
                e.getId(), e.getTenantId(), e.getName(), e.getAgencyCode(), e.getType(), e.getStatus(),
                e.getRegistrationNumber(),
                new AgencyRegistryResponse.AddressResponse(
                        e.getAddrStreet(), e.getAddrLandmark(), e.getAddrQuarter(),
                        e.getAddrCity(), e.getAddrRegion(), e.getAddrCountry(), e.getAddrPostalCode(),
                        e.getAddrLat(), e.getAddrLon()),
                e.getContactEmail(), e.getContactPhone(), e.getLogoUrl(), e.getWebsite(),
                e.getKernelOrganizationId(), e.getKernelBusinessActorId(), e.getCoreAgencyId(),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static AgencySettingsResponse toSettingsResponse(AgencySettingsEntity e) {
        return new AgencySettingsResponse(
                e.getAgencyId(), e.getAutoAssignMissions(), e.getAllowFreelancerAssociation(),
                e.getHubRetentionDelayHours(), e.getDefaultCurrency(), e.getDefaultCommissionRate(),
                e.getMaxActiveBranches(), e.getTimezone());
    }

    public static AgencyBranchResponse toBranchResponse(AgencyBranchEntity e) {
        return new AgencyBranchResponse(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getName(), e.getCode(),
                e.getManagerId(), e.getStatus(), e.getCoreBranchId(),
                new AgencyBranchResponse.AddressResponse(
                        e.getAddrStreet(), e.getAddrLandmark(), e.getAddrQuarter(),
                        e.getAddrCity(), e.getAddrRegion(), e.getAddrCountry(), e.getAddrPostalCode(),
                        e.getAddrLat(), e.getAddrLon()),
                e.getCreatedAt(), e.getUpdatedAt());
    }

    public static AgencyRelayHubResponse toHubResponse(AgencyRelayHubEntity e) {
        int capacity = e.getCapacityUnits() != null ? e.getCapacityUnits() : 0;
        int occupancy = e.getCurrentOccupancy() != null ? e.getCurrentOccupancy() : 0;
        return new AgencyRelayHubResponse(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getBranchId(),
                e.getName(), e.getCode(), e.getStatus(),
                capacity, occupancy, Math.max(0, capacity - occupancy),
                e.getRetentionDelayHours(), e.getOpeningHours(), e.getCoreHubId(),
                e.getAddrCity(), e.getAddrCountry(), e.getAddrStreet(), e.getAddrQuarter(),
                e.getLatitude(), e.getLongitude(), e.getCreatedAt());
    }
}
