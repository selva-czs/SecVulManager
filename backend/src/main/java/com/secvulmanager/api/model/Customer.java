package com.secvulmanager.api.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "customer_name", nullable = false, unique = true)
    private String customerName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "is_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean enabled = true;

    @Column(name = "is_archived", nullable = false, columnDefinition = "boolean default false")
    private boolean archived = false;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "archived_by", length = 100)
    private String archivedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Transient
    private long assignedSoftwareCount;

    @Transient
    private long enabledAssignedSoftwareCount;

    @Transient
    private long activeTemplateCount;

    // Constructors
    public Customer() {}

    public Customer(String customerName, String createdBy) {
        this.customerName = customerName;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public String getArchivedBy() {
        return archivedBy;
    }

    public void setArchivedBy(String archivedBy) {
        this.archivedBy = archivedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public long getAssignedSoftwareCount() {
        return assignedSoftwareCount;
    }

    public void setAssignedSoftwareCount(long assignedSoftwareCount) {
        this.assignedSoftwareCount = assignedSoftwareCount;
    }

    public long getEnabledAssignedSoftwareCount() {
        return enabledAssignedSoftwareCount;
    }

    public void setEnabledAssignedSoftwareCount(long enabledAssignedSoftwareCount) {
        this.enabledAssignedSoftwareCount = enabledAssignedSoftwareCount;
    }

    public long getActiveTemplateCount() {
        return activeTemplateCount;
    }

    public void setActiveTemplateCount(long activeTemplateCount) {
        this.activeTemplateCount = activeTemplateCount;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
