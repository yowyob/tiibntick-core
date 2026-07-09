package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * R2DBC persistence entity for the {@code tnt_client_audit_logs} table. Mapped to/from
 * {@link com.yowyob.tiibntick.core.platformgateway.domain.model.ClientAuditLog} by
 * {@code PlatformClientPersistenceMapper}.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_client_audit_logs")
public class ClientAuditLogEntity {

    @Id
    @Column("id")
    private String id;

    @Column("platform_client_id")
    private String platformClientId;

    @Column("client_id_attempted")
    private String clientIdAttempted;

    @Column("endpoint")
    private String endpoint;

    @Column("http_method")
    private String httpMethod;

    @Column("outcome")
    private String outcome;

    @Column("ip_address")
    private String ipAddress;

    @Column("user_agent")
    private String userAgent;

    @Column("occurred_at")
    private Instant occurredAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPlatformClientId() { return platformClientId; }
    public void setPlatformClientId(String platformClientId) { this.platformClientId = platformClientId; }

    public String getClientIdAttempted() { return clientIdAttempted; }
    public void setClientIdAttempted(String clientIdAttempted) { this.clientIdAttempted = clientIdAttempted; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
