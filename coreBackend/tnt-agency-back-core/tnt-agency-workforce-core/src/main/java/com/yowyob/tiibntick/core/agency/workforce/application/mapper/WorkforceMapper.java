package com.yowyob.tiibntick.core.agency.workforce.application.mapper;

import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.ContractResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.DelivererResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.in.web.dto.FreelancerAssociationResponse;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.ContractEntity;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.DelivererEntity;
import com.yowyob.tiibntick.core.agency.workforce.adapter.out.persistence.entity.FreelancerAssociationEntity;
import com.yowyob.tiibntick.core.agency.workforce.domain.Contract;
import com.yowyob.tiibntick.core.agency.workforce.domain.Deliverer;
import com.yowyob.tiibntick.core.agency.workforce.domain.FreelancerAssociation;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.AssociationStatus;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.ContractStatus;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.ContractType;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.DelivererStatus;
import com.yowyob.tiibntick.core.agency.workforce.domain.vo.RemunerationModel;

public final class WorkforceMapper {

    private WorkforceMapper() {}

    public static DelivererResponse toDelivererResponse(Deliverer d) {
        return new DelivererResponse(
                d.getId(), d.getTenantId(), d.getAgencyId(), d.getBranchId(), d.getActorId(),
                d.getPhone(), d.getStatus().name(), d.getJoinedAt(), d.getSuspendedAt());
    }

    public static ContractResponse toContractResponse(Contract c) {
        return new ContractResponse(
                c.getId(), c.getTenantId(), c.getAgencyId(), c.getDelivererId(),
                c.getContractType().name(), c.getStartDate(), c.getEndDate(),
                c.getRemunerationModel().name(), c.getBaseSalary(), c.getCommissionRate(),
                c.getStatus().name(), c.getSignedAt());
    }

    public static FreelancerAssociationResponse toAssociationResponse(FreelancerAssociation a) {
        return new FreelancerAssociationResponse(
                a.getId(), a.getTenantId(), a.getAgencyId(), a.getFreelancerActorId(),
                a.getCommissionRate(), a.getStartDate(), a.getEndDate(),
                a.getStatus().name(), a.getAssociatedAt());
    }

    public static DelivererEntity toDelivererEntity(Deliverer d) {
        DelivererEntity e = new DelivererEntity();
        e.setId(d.getId());
        e.setTenantId(d.getTenantId());
        e.setAgencyId(d.getAgencyId());
        e.setBranchId(d.getBranchId());
        e.setActorId(d.getActorId());
        e.setPhone(d.getPhone());
        e.setStatus(d.getStatus().name());
        e.setJoinedAt(d.getJoinedAt());
        e.setSuspendedAt(d.getSuspendedAt());
        e.setCreatedAt(d.getCreatedAt());
        e.setUpdatedAt(d.getUpdatedAt());
        e.setVersion(d.getVersion());
        return e;
    }

    public static Deliverer toDelivererDomain(DelivererEntity e) {
        return new Deliverer(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getBranchId(), e.getActorId(), e.getPhone(),
                DelivererStatus.valueOf(e.getStatus()),
                e.getJoinedAt(), e.getSuspendedAt(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getVersion() != null ? e.getVersion() : 0L);
    }

    public static ContractEntity toContractEntity(Contract c) {
        ContractEntity e = new ContractEntity();
        e.setId(c.getId());
        e.setTenantId(c.getTenantId());
        e.setAgencyId(c.getAgencyId());
        e.setDelivererId(c.getDelivererId());
        e.setContractType(c.getContractType().name());
        e.setStartDate(c.getStartDate());
        e.setEndDate(c.getEndDate());
        e.setRemunerationModel(c.getRemunerationModel().name());
        e.setBaseSalary(c.getBaseSalary());
        e.setCommissionRate(c.getCommissionRate());
        e.setStatus(c.getStatus().name());
        e.setSignedAt(c.getSignedAt());
        e.setCreatedAt(c.getCreatedAt());
        e.setUpdatedAt(c.getUpdatedAt());
        e.setVersion(c.getVersion());
        return e;
    }

    public static Contract toContractDomain(ContractEntity e) {
        return new Contract(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getDelivererId(),
                ContractType.valueOf(e.getContractType()),
                e.getStartDate(), e.getEndDate(),
                RemunerationModel.valueOf(e.getRemunerationModel()),
                e.getBaseSalary(), e.getCommissionRate(),
                ContractStatus.valueOf(e.getStatus()),
                e.getSignedAt(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }

    public static FreelancerAssociationEntity toAssociationEntity(FreelancerAssociation a) {
        FreelancerAssociationEntity e = new FreelancerAssociationEntity();
        e.setId(a.getId());
        e.setTenantId(a.getTenantId());
        e.setAgencyId(a.getAgencyId());
        e.setFreelancerActorId(a.getFreelancerActorId());
        e.setCommissionRate(a.getCommissionRate());
        e.setStartDate(a.getStartDate());
        e.setEndDate(a.getEndDate());
        e.setStatus(a.getStatus().name());
        e.setAssociatedAt(a.getAssociatedAt());
        e.setCreatedAt(a.getCreatedAt());
        e.setUpdatedAt(a.getUpdatedAt());
        e.setVersion(a.getVersion());
        return e;
    }

    public static FreelancerAssociation toAssociationDomain(FreelancerAssociationEntity e) {
        return new FreelancerAssociation(
                e.getId(), e.getTenantId(), e.getAgencyId(), e.getFreelancerActorId(),
                e.getCommissionRate(), e.getStartDate(), e.getEndDate(),
                AssociationStatus.valueOf(e.getStatus()),
                e.getAssociatedAt(), e.getCreatedAt(), e.getUpdatedAt(),
                e.getVersion() != null ? e.getVersion() : 0L);
    }
}
