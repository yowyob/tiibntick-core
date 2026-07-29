package com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.GeoPointField;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import java.util.UUID;
import java.time.Instant;

/**
 * Elasticsearch document representing an announcement.
 * Used for matching candidates based on proximity.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "announcements", createIndex = true)
public class AnnouncementDocument {

    @Id
    private UUID id;

    @Field(type = FieldType.Keyword)
    private UUID clientId;

    @GeoPointField
    private GeoPoint pickupLocation;

    @GeoPointField
    private GeoPoint deliveryLocation;

    @Field(type = FieldType.Object)
    private PacketDTO packet;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Double)
    private Double amount;

    /**
     * Type de véhicule requis par le client (optionnel).
     * Null = tous les types acceptés.
     * Propagé au TopsisRankingService lors du re-ranking.
     */
    @Field(type = FieldType.Keyword)
    private String requiredVehicleType;
}
