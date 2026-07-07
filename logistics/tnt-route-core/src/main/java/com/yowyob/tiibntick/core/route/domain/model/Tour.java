package com.yowyob.tiibntick.core.route.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public final class Tour {

    private final UUID id;
    private final UUID tenantId;
    private final String delivererId;
    private final List<TourStop> stops;
    private final double totalCost;
    private final double totalDistanceKm;
    private final LocalDate planningDate;
    private TourStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Tour(UUID id, UUID tenantId, String delivererId, List<TourStop> stops,
                 double totalCost, double totalDistanceKm, LocalDate planningDate,
                 TourStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.delivererId = Objects.requireNonNull(delivererId);
        this.stops = new ArrayList<>(stops);
        this.totalCost = totalCost;
        this.totalDistanceKm = totalDistanceKm;
        this.planningDate = Objects.requireNonNull(planningDate);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Tour create(UUID tenantId, String delivererId, List<TourStop> stops,
                              double totalCost, double totalDistanceKm, LocalDate planningDate) {
        Instant now = Instant.now();
        return new Tour(UUID.randomUUID(), tenantId, delivererId, stops,
                totalCost, totalDistanceKm, planningDate, TourStatus.PLANNED, now, now);
    }

    public static Tour rehydrate(UUID id, UUID tenantId, String delivererId,
                                  List<TourStop> stops, double totalCost, double totalDistanceKm,
                                  LocalDate planningDate, TourStatus status,
                                  Instant createdAt, Instant updatedAt) {
        return new Tour(id, tenantId, delivererId, stops, totalCost, totalDistanceKm,
                planningDate, status, createdAt, updatedAt);
    }

    public void start()    { this.status = TourStatus.IN_PROGRESS; this.updatedAt = Instant.now(); }
    public void complete() { this.status = TourStatus.COMPLETED;   this.updatedAt = Instant.now(); }
    public void cancel()   { this.status = TourStatus.CANCELLED;   this.updatedAt = Instant.now(); }

    public UUID id()                   { return id; }
    public UUID tenantId()             { return tenantId; }
    public String delivererId()        { return delivererId; }
    public List<TourStop> stops()      { return Collections.unmodifiableList(stops); }
    public double totalCost()          { return totalCost; }
    public double totalDistanceKm()    { return totalDistanceKm; }
    public LocalDate planningDate()    { return planningDate; }
    public TourStatus status()         { return status; }
    public Instant createdAt()         { return createdAt; }
    public Instant updatedAt()         { return updatedAt; }
    public int stopCount()             { return stops.size(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tour t)) return false;
        return id.equals(t.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
