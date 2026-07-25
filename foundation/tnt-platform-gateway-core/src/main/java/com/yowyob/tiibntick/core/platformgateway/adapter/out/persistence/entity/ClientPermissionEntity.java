package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity;
import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;


import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * R2DBC persistence entity for the {@code tnt_client_permissions} table. Mapped to/from
 * {@link com.yowyob.tiibntick.core.platformgateway.domain.model.ClientPermission} by
 * {@code PlatformClientPersistenceMapper}.
 *
 * @author MANFOUO Braun
 */
@Table("tnt_client_permissions")
public class ClientPermissionEntity implements Persistable<String>, TntPersistableEntity {

    @Id
    @Column("id")
    private String id;

    @Transient
    private boolean isNew = true;

    @Column("platform_client_id")
    private String platformClientId;

    @Column("scope")
    private String scope;

    @Column("granted_at")
    private Instant grantedAt;

    @Column("granted_by")
    private String grantedBy;

    @Override
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @Override
    public boolean isNew() { return isNew; }

    @Override
    public void markNotNew() { this.isNew = false; }

    public String getPlatformClientId() { return platformClientId; }
    public void setPlatformClientId(String platformClientId) { this.platformClientId = platformClientId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Instant getGrantedAt() { return grantedAt; }
    public void setGrantedAt(Instant grantedAt) { this.grantedAt = grantedAt; }

    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
}
