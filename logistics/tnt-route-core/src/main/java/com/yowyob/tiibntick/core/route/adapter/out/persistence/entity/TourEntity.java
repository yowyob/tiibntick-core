package com.yowyob.tiibntick.core.route.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Table(schema = "tnt_route", value = "tours")
public class TourEntity implements Persistable<UUID> {
    @Id @Column("id") private UUID id;

    @Transient
    private boolean isNew;
    @Column("tenant_id") private UUID tenantId;
    @Column("deliverer_id") private String delivererId;
    @Column("total_cost") private double totalCost;
    @Column("total_distance_km") private double totalDistanceKm;
    @Column("planning_date") private LocalDate planningDate;
    @Column("status") private String status;
    @Column("stops_json") private String stopsJson;
    @Column("created_at") private Instant createdAt;
    @Column("updated_at") private Instant updatedAt;

    public TourEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    @Override public boolean isNew() { return isNew; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID t) { this.tenantId = t; }
    public String getDelivererId() { return delivererId; }
    public void setDelivererId(String d) { this.delivererId = d; }
    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double c) { this.totalCost = c; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double d) { this.totalDistanceKm = d; }
    public LocalDate getPlanningDate() { return planningDate; }
    public void setPlanningDate(LocalDate d) { this.planningDate = d; }
    public String getStatus() { return status; }
    public void setStatus(String s) { this.status = s; }
    public String getStopsJson() { return stopsJson; }
    public void setStopsJson(String s) { this.stopsJson = s; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant t) { this.createdAt = t; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant t) { this.updatedAt = t; }
}
