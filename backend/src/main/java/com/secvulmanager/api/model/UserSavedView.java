package com.secvulmanager.api.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserSavedView {
    private UUID id;
    private UUID userId;
    private String name;
    private String viewType;
    private boolean defaultView;
    private String filtersJson;
    private String sortJson;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getViewType() {
        return viewType;
    }

    public void setViewType(String viewType) {
        this.viewType = viewType;
    }

    public boolean isDefaultView() {
        return defaultView;
    }

    public void setDefaultView(boolean defaultView) {
        this.defaultView = defaultView;
    }

    public String getFiltersJson() {
        return filtersJson;
    }

    public void setFiltersJson(String filtersJson) {
        this.filtersJson = filtersJson;
    }

    public String getSortJson() {
        return sortJson;
    }

    public void setSortJson(String sortJson) {
        this.sortJson = sortJson;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
