package com.yowyob.tiibntick.core.agency.staff.adapter.out.persistence.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table(schema = "agency_hr", name = "staff_members")
public class StaffMemberEntity {

    @Id @Column("id")           private UUID id;
    @Column("tenant_id")        private UUID tenantId;
    @Column("agency_id")        private UUID agencyId;
    @Column("branch_id")        private UUID branchId;
    @Column("full_name")        private String fullName;
    @Column("phone")            private String phone;
    @Column("email")            private String email;
    @Column("role")             private String role;
    @Column("status")           private String status;
    @Column("joined_at")        private Instant joinedAt;
    @Column("suspended_at")      private Instant suspendedAt;
    @Column("created_at")       private Instant createdAt;
    @Column("updated_at")       private Instant updatedAt;
    @Version @Column("version") private Long version;
}
